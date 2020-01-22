package com.megazone.megatong;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.Page;
import com.amazonaws.services.dynamodbv2.document.QueryOutcome;
import com.amazonaws.services.dynamodbv2.document.ScanOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
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
import model.List;
import model.PhotoContent;
import model.PhotoInfo;
import model.User;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


public class ListHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
	AmazonDynamoDB client = (AmazonDynamoDB) AmazonDynamoDBClientBuilder.standard().build();
	DynamoDB dynamoDB = new DynamoDB(client);
	Table ContentTable = dynamoDB.getTable("content");

	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
		Map<String, String> feedInfo = event.getQueryStringParameters();
		APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
		String feedType = (String) feedInfo.get("id");
		String orderBy = (String) feedInfo.get("order_by");

		try {
			JSONParser parser = new JSONParser();
			JSONObject JSONresponse;

			if (orderBy.equals("latest") && feedType.equals("all")) {
				context.getLogger().log("event :" + event.toString());
				JSONresponse = (JSONObject) parser.parse(getAllFeeds(event, feedInfo, context));
				response.setBody(JSONresponse.toString());
			} else if (orderBy.equals("popular")) {
				JSONresponse = (JSONObject) parser.parse(getPopularFeeds(event, feedInfo, context));
				response.setBody(JSONresponse.toString());
			} else if (!feedType.equals("all") && !feedType.equals("popular")) {
				JSONresponse = (JSONObject) parser.parse(getUserFeeds(event, feedInfo, context));
				response.setBody(JSONresponse.toString());
			}
		} catch (ParseException e) {
			e.printStackTrace();
			response.setBody(e.toString());
		}

		return response;
	}

	public String getAllFeeds(APIGatewayProxyRequestEvent event, Map<String, String> feedInfo, Context context) {
		try {
			ArrayList<Content> contentList = new ArrayList<Content>();
			List listItem = new List();

			ScanSpec scanSpec = new ScanSpec().withProjectionExpression("id");
			ItemCollection<ScanOutcome> items = ContentTable.scan(scanSpec);
			Iterator<Item> iter = items.iterator();
			Item IdItem = null;

			int page = Integer.parseInt((String) feedInfo.get("page"));

			int totalCnt = 0;

			while(iter.hasNext()) {
				IdItem = iter.next();
				//context.getLogger().log("item : " + IdItem.toString());
				totalCnt++;
			}

			context.getLogger().log("totalCnt : " + totalCnt);

			int perPage = Integer.parseInt((String) feedInfo.get("per_page"));
			int totalPage = (totalCnt / perPage) + 1; 

			listItem.setTotal(totalCnt);
			listItem.setPerPage(perPage);
			listItem.setPages(totalPage);

			context.getLogger().log("totalPage : " + totalPage);

			Map<String, Object> expressionAttributeValues = new HashMap<String, Object>();

			expressionAttributeValues.put(":organization", "megazone");
			QuerySpec querySpec = new QuerySpec()
					.withKeyConditionExpression("organization = :organization").withScanIndexForward(true)
					.withValueMap(expressionAttributeValues).withMaxPageSize(perPage);

			ItemCollection<QueryOutcome> PagingItems = ContentTable.query(querySpec);

			Page<Item, QueryOutcome> firstItem = PagingItems.firstPage();
			if(page == 1) {
				for (Item latestItem : PagingItems.firstPage()) {
					context.getLogger().log("pagingItem :" + latestItem.toString());

					ArrayList<PhotoInfo> photoList = new ArrayList<PhotoInfo>();
					PhotoContent photoContent = new PhotoContent();
					Content results = new Content();

					results.setId(latestItem.getString("id"));
					results.setCreated_at(latestItem.getLong("created_at"));
					results.setUpdated_at(latestItem.getLong("updated_at"));
					results.setLikes(latestItem.getInt("likes"));
					results.setRanking(latestItem.getInt("ranking"));
					results.setMedia(latestItem.getString("media"));
					results.setDescription(latestItem.getJSON("description"));

					int photoCnt = 0;
					for (int i = 1; i < 5; i++) {
						if (latestItem.isPresent("photos" + i)) {
							PhotoInfo photoInfo = new PhotoInfo();
							String photoUrl = latestItem.getString("photos" + i);
							String fileId = latestItem.getString("id");

							photoInfo.setId(fileId);
							photoInfo.setPhoto(photoUrl);
							photoList.add(photoInfo);
						}
						photoCnt = photoList.size();
					}
					photoContent.setCount(photoCnt);
					photoContent.setPhotos(photoList);
					results.setPhotoContent(photoContent);

					String userId = latestItem.getString("userId");

					results.setUser(UserInfo(userId));
					contentList.add(results);
				}
			}else if(page != 1) {
				Page<Item, QueryOutcome> nextItem = firstItem;
				for (int i = 1; i < page; i++) {
					nextItem = nextItem.nextPage();
				}
				for (Item latestItem : nextItem) {
					ArrayList<PhotoInfo> photoList = new ArrayList<PhotoInfo>();
					PhotoContent photoContent = new PhotoContent();
					Content results = new Content();

					results.setId(latestItem.getString("id"));
					results.setCreated_at(latestItem.getLong("created_at"));
					results.setUpdated_at(latestItem.getLong("updated_at"));
					results.setLikes(latestItem.getInt("likes"));
					results.setRanking(latestItem.getInt("ranking"));
					results.setMedia(latestItem.getString("media"));
					results.setDescription(latestItem.getJSON("description"));

					int photoCnt = 0;
					for (int j = 1; j < 5; j++) {
						if (latestItem.isPresent("photos" + j)) {
							PhotoInfo photoInfo = new PhotoInfo();
							String photoUrl = latestItem.getString("photos" + j);
							String fileId = latestItem.getString("id");

							photoInfo.setId(fileId);
							photoInfo.setPhoto(photoUrl);
							photoList.add(photoInfo);
						}
						photoCnt = photoList.size();
					}
					photoContent.setCount(photoCnt);
					photoContent.setPhotos(photoList);
					results.setPhotoContent(photoContent);

					String userId = latestItem.getString("userId");

					results.setUser(UserInfo(userId));
					contentList.add(results);
				}
			}

			listItem.setResults(contentList);

			ObjectMapper mapper = new ObjectMapper();
			String jsonInString = mapper.writeValueAsString(listItem);

			return jsonInString;
		} catch (Exception e) {
			e.printStackTrace();
			return e.toString();
		}
	}

	public String getUserFeeds(APIGatewayProxyRequestEvent event, Map<String, String> feedInfo, Context context) {
		try {
			ArrayList<Content> contentList = new ArrayList<Content>();
			List listItem = new List();

			int page = Integer.parseInt((String) feedInfo.get("page"));
			int perPage = Integer.parseInt((String) feedInfo.get("per_page"));

			Map<String, AttributeValue> expressionUserContentCnt = new HashMap<String, AttributeValue>();
			expressionUserContentCnt.put(":ui", new AttributeValue().withS(feedInfo.get("id")));

			ScanRequest scanRequest = new ScanRequest().withTableName("content").withFilterExpression("userId = :ui")
					.withExpressionAttributeValues(expressionUserContentCnt);
			ScanResult result = client.scan(scanRequest);

			int totalCnt = result.getCount();
			int totalPage = (totalCnt / perPage) + 1;

			listItem.setTotal(totalCnt);
			listItem.setPerPage(perPage);
			listItem.setPages(totalPage);

			QuerySpec querySpec = new QuerySpec()
					.withKeyConditionExpression("organization = :organization")
					.withFilterExpression("userId = :ui")
					.withValueMap(new ValueMap()
							.withString(":ui", feedInfo.get("id"))
							.withString(":organization", "megazone"))
					.withScanIndexForward(true);

			ItemCollection<QueryOutcome> items = ContentTable.query(querySpec);	
			ArrayList<Item> separateItems = new ArrayList<Item>();

			Iterator<Item> iterator = items.iterator();
			while (iterator.hasNext()) {
				separateItems.add(iterator.next());
				context.getLogger().log("separateItem : " + separateItems.toString());
			}
			
			java.util.List<Item> nowList = null;
			
			if(separateItems.size() < perPage) {
				nowList = separateItems;
			}else{
				nowList = separateItems.subList((page - 1) * perPage, page * perPage);
			}
			Iterator<Item> itemKey = nowList.iterator();
			
			while (itemKey.hasNext()) {
				ArrayList<PhotoInfo> photoList = new ArrayList<PhotoInfo>();
				PhotoContent photoContent = new PhotoContent();
				Content results = new Content();
				
				Item nowItem = (Item) itemKey.next();

				results.setId(nowItem.getString("id"));
				results.setCreated_at(nowItem.getLong("created_at"));
				results.setUpdated_at(nowItem.getLong("updated_at"));
				results.setLikes(nowItem.getInt("likes"));
				results.setRanking(nowItem.getInt("ranking"));
				results.setMedia(nowItem.getString("media"));
				results.setDescription(nowItem.getJSON("description"));

				String userId = nowItem.getString("userId");

				int photoCnt = 0;
				for (int i = 1; i < 5; i++) {
					if (nowItem.isPresent("photos" + i)) {
						PhotoInfo photoInfo = new PhotoInfo();
						String photoUrl = nowItem.getString("photos" + i);
						String fileId = nowItem.getString("id");

						photoInfo.setId(fileId);
						photoInfo.setPhoto(photoUrl);
						photoList.add(photoInfo);
					}
					photoCnt = photoList.size();
				}

				photoContent.setCount(photoCnt);
				photoContent.setPhotos(photoList);
				results.setPhotoContent(photoContent);
				results.setUser(UserInfo(userId));
				contentList.add(results);

			}
			
			listItem.setResults(contentList);

			ObjectMapper mapper = new ObjectMapper();
			String jsonInString = mapper.writeValueAsString(listItem);

			return jsonInString;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public String getPopularFeeds(APIGatewayProxyRequestEvent event, Map<String, String> feedInfo, Context context) {
		try {
			Table SortingTable = dynamoDB.getTable("likes-by-organization");

			ArrayList<Content> contentList = new ArrayList<Content>();
			List listItem = new List();

			int page = Integer.parseInt((String) feedInfo.get("page"));
			int perPage = Integer.parseInt((String) feedInfo.get("per_page"));

			String feedId = null;
			Map<Integer, String> ranking = new HashMap<Integer, String>();
			ItemCollection<QueryOutcome> items = null;
			Iterator<Item> iterator = null;
			Item rankItem = null;

			Map<String, Object> expressionAttributeValues = new HashMap<String, Object>();

			expressionAttributeValues.put(":organization", "megazone");
			QuerySpec querySpec = new QuerySpec().withScanIndexForward(false)
					.withKeyConditionExpression("organization = :organization")
					.withValueMap(expressionAttributeValues);
			items = SortingTable.query(querySpec);

			iterator = items.iterator();
			int rank = 1;
			while(iterator.hasNext()) {
				rankItem = iterator.next();
				context.getLogger().log("rankItem: " + rankItem.toString());
				String likeHash = rankItem.getString("likeHash");
				feedId = likeHash.substring(likeHash.length() - 36, likeHash.length());
				ranking.put(rank, feedId);

				rank++;
			}

			int totalCnt = ranking.size();
			int totalPage = (totalCnt / perPage) + 1; 

			listItem.setTotal(totalCnt);
			listItem.setPerPage(perPage);
			listItem.setPages(totalPage);

			ArrayList<ItemCollection<QueryOutcome>> PagingItems = new ArrayList<ItemCollection<QueryOutcome>>();

			Iterator<Integer> rankingKeys = ranking.keySet().iterator();
			while(rankingKeys.hasNext()){
				int key = rankingKeys.next();
				QuerySpec ContentQuerySpec = new QuerySpec()
						.withKeyConditionExpression("organization = :organization")
						.withFilterExpression("id = :id")
						.withValueMap(new ValueMap()
								.withString(":organization", "megazone")
								.withString(":id", ranking.get(key)))
						//.withExclusiveStartKey("organization", "megazone", "created_at", createdAt)
						.withMaxPageSize(perPage);

				PagingItems.add(ContentTable.query(ContentQuerySpec));
			}

			java.util.List<ItemCollection<QueryOutcome>> nowList = PagingItems.subList((page - 1) * perPage, page * perPage);
			Iterator<ItemCollection<QueryOutcome>> itemKey = nowList.iterator();

			while (itemKey.hasNext()) {
				ItemCollection<QueryOutcome> latestItem = itemKey.next();
				iterator = latestItem.iterator();

				while (iterator.hasNext()) {
					ArrayList<PhotoInfo> photoList = new ArrayList<PhotoInfo>();
					PhotoContent photoContent = new PhotoContent();
					Content results = new Content();

					context.getLogger().log("item : " + latestItem.toString());

					Item nowItem = (Item) iterator.next();

					String userId = nowItem.getString("userId");

					results.setId(nowItem.getString("id"));
					results.setCreated_at(nowItem.getLong("created_at"));
					results.setUpdated_at(nowItem.getLong("updated_at"));
					results.setLikes(nowItem.getInt("likes"));
					results.setRanking(nowItem.getInt("ranking"));
					results.setMedia(nowItem.getString("media"));
					results.setDescription(nowItem.getJSON("description"));
					results.setUser(UserInfo(userId));

					int photoCnt = 0;
					for (int j = 0; j < 4; ++j) {
						if (nowItem.isPresent("photos" + j)) {
							PhotoInfo photoInfo = new PhotoInfo();
							String photoUrl = nowItem.getString("photos" + j);
							String fileId = nowItem.getString("id");

							photoInfo.setId(fileId + "@" + userId);
							photoInfo.setPhoto(photoUrl);
							photoList.add(photoInfo);
						}
						photoCnt = photoList.size();
					}
					photoContent.setCount(photoCnt);
					photoContent.setPhotos(photoList);
					results.setPhotoContent(photoContent);

					results.setUser(UserInfo(userId));

					contentList.add(results);
				}
			}

			listItem.setTotal(totalCnt);
			listItem.setPerPage(perPage);
			listItem.setPages(totalPage);

			listItem.setResults(contentList);

			ObjectMapper mapper = new ObjectMapper();
			String jsonInString = mapper.writeValueAsString(listItem);

			return jsonInString;

		} catch (Exception e) {
			e.printStackTrace();
			return e.toString();
		}
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
}
