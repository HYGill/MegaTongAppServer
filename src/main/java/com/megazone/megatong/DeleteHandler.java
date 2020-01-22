package com.megazone.megatong;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.DeleteItemSpec;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ReturnValue;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import model.Content;
import model.PhotoContent;
import model.PhotoInfo;
import model.User;


public class DeleteHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

	AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
			.withRegion(clientRegion)
			.build();
	
	AmazonDynamoDB dbClient = AmazonDynamoDBClientBuilder.standard().build();
	DynamoDB dynamoDB = new DynamoDB(dbClient);

	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
		Content content = new Content();
		ArrayList<PhotoInfo> photoList = new ArrayList<PhotoInfo>();
		APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
		
		try {
			Table ContentTable = dynamoDB.getTable("content");
			Table ContentIndex = dynamoDB.getTable("createdIndex");
			Table SortingTable = dynamoDB.getTable("likes-by-organization");
			
			String feedId = (String) event.getPathParameters().get("id");
			
			PhotoContent photoContent = new PhotoContent();
			
			Item contentIndexItem = ContentIndex.getItem("id", feedId);
			long createdAt = contentIndexItem.getLong("created_at");

			GetItemSpec contentId = new GetItemSpec().withPrimaryKey("organization", "megazone", "created_at", createdAt);

			Item contentItem = ContentTable.getItem(contentId);

			String userId = contentItem.getString("userId");
			content.setId(contentItem.getString("id"));
			content.setCreated_at(contentItem.getLong("created_at"));
			content.setUpdated_at(contentItem.getLong("updated_at"));
			content.setLikes(contentItem.getInt("likes"));
			content.setRanking(contentItem.getInt("ranking"));
			content.setMedia(contentItem.getString("media"));
			content.setDescription(contentItem.getJSON("description"));
			content.setUser(UserInfo(userId));

			for (int i = 0; i < 4; ++i) {
				if (contentItem.isPresent("photos" + i)) {
					PhotoInfo photoInfo = new PhotoInfo();
					String photoUrl = contentItem.getString("photos" + i);
					String fileId = contentItem.getString("id");

					photoInfo.setId(fileId + "@" + userId);
					photoInfo.setPhoto(photoUrl);
					photoList.add(photoInfo);
				}
			}
			photoContent.setCount(photoList.size());
			photoContent.setPhotos(photoList);
			content.setPhotoContent(photoContent);

			int like = contentItem.getInt("likes");
			String likeHash = String.format("%05d", like) + feedId;

			context.getLogger().log(likeHash);

			DeleteItemSpec deleteContentItem = new DeleteItemSpec().withPrimaryKey("organization", "megazone", "created_at", createdAt)
					.withReturnValues(ReturnValue.ALL_OLD);
			ContentTable.deleteItem(deleteContentItem);
			
			DeleteItemSpec deleteIndexItem = new DeleteItemSpec().withPrimaryKey("id", feedId)
					.withReturnValues(ReturnValue.ALL_OLD);
			ContentIndex.deleteItem(deleteIndexItem);
			
			DeleteItemSpec deleteSortingItem = new DeleteItemSpec().withPrimaryKey("organization", "megazone", "likeHash", likeHash)
					.withReturnValues(ReturnValue.ALL_OLD);
			SortingTable.deleteItem(deleteSortingItem);

			ObjectListing objectListing = s3Client.listObjects(bucketName, userId + "/" + feedId + "/");
			List<S3ObjectSummary> objects = objectListing.getObjectSummaries();

			for (S3ObjectSummary objectSummary : objects) {
				s3Client.deleteObject(new DeleteObjectRequest(bucketName, objectSummary.getKey()));
				context.getLogger().log("getKey:" + objectSummary.getKey());
			}

			ObjectMapper mapper = new ObjectMapper();
			String jsonInString = mapper.writeValueAsString(content);
			response.setBody(jsonInString);

		} catch (Exception e) {
			e.printStackTrace();
			response.setBody(e.toString());
		}

		return response;
	}

	public User UserInfo(String id) {
		User user = new User();
		Table UserTable = this.dynamoDB.getTable("user");
		Item userInfo = UserTable.getItem("id", id);
		
		user.setId(userInfo.getString("id"));
		user.setEmail(userInfo.getString("email"));
		user.setEmail_verified(userInfo.getBoolean("email_verified"));
		user.setName(userInfo.getString("name"));
		user.setPhone(userInfo.getString("phone"));
		user.setPhoto(userInfo.getString("photo"));
		
		return user;
	}
}