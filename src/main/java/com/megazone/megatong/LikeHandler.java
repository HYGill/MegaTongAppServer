package com.megazone.megatong;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.QueryOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.amazonaws.services.dynamodbv2.document.utils.NameMap;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ReturnValue;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import model.Content;
import model.PhotoContent;
import model.PhotoInfo;
import model.User;


public class LikeHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
	AmazonDynamoDB client = (AmazonDynamoDB) AmazonDynamoDBClientBuilder.standard().build();
	DynamoDB dynamoDB = new DynamoDB(client);
	Table ContentTable = dynamoDB.getTable("content");
	Table SortingTable = dynamoDB.getTable("likes-by-organization");
	Table ContentIndex = dynamoDB.getTable("createdIndex");

	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
		APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
		
		Content content = new Content();
		String feedId = (String) event.getPathParameters().get("id");
		
		Item contentIndexItem = ContentIndex.getItem("id", feedId);
		long createdAt = contentIndexItem.getLong("created_at");
		
		Item originContentItem = ContentTable.getItem("organization", "megazone", "created_at", createdAt);
		
		int like = originContentItem.getInt("likes");
		updateRank(SortingTable, like, feedId, originContentItem.getString("userId"));

		try {
			ArrayList<PhotoInfo> photoList = new ArrayList<PhotoInfo>();
			PhotoContent photoContent = new PhotoContent();
			
			UpdateItemSpec updateItem = (new UpdateItemSpec())
					.withPrimaryKey("organization", "megazone", "created_at", createdAt)
					.withUpdateExpression("set likes = likes + :val")
					.withValueMap((new ValueMap()).withNumber(":val", 1)).withReturnValues(ReturnValue.UPDATED_NEW);
			
			ContentTable.updateItem(updateItem);
			
			GetItemSpec contentId = new GetItemSpec().withPrimaryKey("organization", "megazone", "created_at", createdAt);
			Item contentItem = ContentTable.getItem(contentId);
			
			Map<Integer, Long> ranking = rankCacul();
			System.out.println(ranking);
			
			Iterator keys = ranking.keySet().iterator();

			while (keys.hasNext()) {
				Object key = keys.next();
				context.getLogger().log("key:" + key.toString());
				UpdateItemSpec updateRank = (new UpdateItemSpec())
						.withPrimaryKey("organization", "megazone", "created_at", (Long) ranking.get(key))
						.withUpdateExpression("set #ra = :val1").withNameMap((new NameMap()).with("#ra", "ranking"))
						.withValueMap((new ValueMap()).withInt(":val1", (Integer) key))
						.withReturnValues(ReturnValue.UPDATED_NEW);
				ContentTable.updateItem(updateRank);
			}

			Item contentIncludeRank = ContentTable.getItem("organization", "megazone", "created_at", createdAt);
			String userId = contentItem.getString("userId");

			int photoCnt = 0;
			for (int i = 0; i < 4; ++i) {
				if (contentItem.isPresent("photos" + i)) {
					PhotoInfo photoInfo = new PhotoInfo();
					String photoUrl = contentItem.getString("photos" + i);
					String fileId = contentItem.getString("id");
					photoInfo.setId(fileId + "@" + userId);
					photoInfo.setPhoto(photoUrl);
					photoList.add(photoInfo);
				}

				photoCnt = photoList.size();
			}


			photoContent.setCount(photoCnt);
			photoContent.setPhotos(photoList);
			content.setPhotoContent(photoContent);
			content.setId(contentIncludeRank.getString("id"));
			content.setCreated_at(contentIncludeRank.getLong("created_at"));
			content.setUpdated_at(contentIncludeRank.getLong("updated_at"));
			content.setLikes(contentIncludeRank.getInt("likes"));
			content.setRanking(contentIncludeRank.getInt("ranking"));
			content.setMedia(contentIncludeRank.getString("media"));
			content.setDescription(contentIncludeRank.getJSON("description"));
			content.setUser(UserInfo(userId));
			
			ObjectMapper mapper = new ObjectMapper();
			String jsonInString = mapper.writeValueAsString(content);
			
			response.setBody(jsonInString);

		} catch (Exception e) {
			e.printStackTrace();
			response.setBody(e.toString());
		}
		return response;
	}

	public Map<Integer, Long> rankCacul() {
		Map<Integer, Long> ranking = new HashMap<Integer, Long>();
		ItemCollection<QueryOutcome> items = null;
		Iterator<Item> iterator = null;
		Item item = null;
		
		Map<String, Object> expressionAttributeValues = new HashMap<String, Object>();
		
		expressionAttributeValues.put(":organization", "megazone");
		QuerySpec querySpec = (new QuerySpec()).withScanIndexForward(false)
				.withKeyConditionExpression("organization = :organization").withScanIndexForward(false)
				.withValueMap(expressionAttributeValues).withMaxResultSize(5);
		items = this.SortingTable.query(querySpec);
		iterator = items.iterator();

		for (int rank = 1; iterator.hasNext(); ++rank) {
			item = iterator.next();
			String likeHash = item.getString("likeHash");
			String feedId = likeHash.substring(likeHash.length() - 36, likeHash.length());
			Item contentIndexItem = ContentIndex.getItem("id", feedId);
			long createdAt = contentIndexItem.getLong("created_at");
			ranking.put(rank, createdAt);
		}

		return ranking;
	}

	public String updateRank(Table SortingTable, int like, String feedId, String userId) {
		String originLike = String.format("%05d", like) + feedId;
		String updateLike = String.format("%05d", like + 1) + feedId;
		
		HashMap<String, AttributeValue> deleteKey = new HashMap<String, AttributeValue>();
		
		deleteKey.put("likeHash", new AttributeValue(originLike));
		deleteKey.put("organization", new AttributeValue("megazone"));
		
		client.deleteItem("likes-by-organization", deleteKey);
		
		Item item = (new Item()).withPrimaryKey("organization", "megazone", "likeHash", updateLike).withString("id",userId);
		SortingTable.putItem(item);
		
		return updateLike;
	}

	public User UserInfo(String id) {
		User user = new User();
		
		Table UserTable = dynamoDB.getTable("user");
		Item userInfo = UserTable.getItem("id", id);
		
		user.setId(userInfo.getString("id"));
		user.setEmail(userInfo.getString("email"));
		user.setEmail_verified(userInfo.getBoolean("email_verified"));
		user.setName(userInfo.getString("name"));
		user.setPhone(userInfo.getString("phone"));
		user.setPhoto(userInfo.getString("photo"));
		
		return user;
	}

	public Content ContentInfo(String id) {
		Content content = new Content();
		
		GetItemSpec contentId = (new GetItemSpec()).withPrimaryKey("id", id);
		Item contentItem = ContentTable.getItem(contentId);
		
		content.setId(contentItem.getString("id"));
		content.setCreated_at(contentItem.getLong("created_at"));
		content.setUpdated_at(contentItem.getLong("updated_at"));
		content.setLikes(contentItem.getInt("likes"));
		content.setRanking(contentItem.getInt("ranking"));
		content.setMedia(contentItem.getString("media"));
		content.setDescription(contentItem.getJSON("description"));
		return content;
		
	}
}