package com.megazone.megatong;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class UserInfoHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
	AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
	DynamoDB dynamoDB  = new DynamoDB(client);

	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
		APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
		
		try {
			JSONParser parser = new JSONParser();
			JSONObject request = (JSONObject) parser.parse(event.getBody());
			
			Table UserTable = dynamoDB.getTable("user");
			Item item = new Item().withPrimaryKey("id", (String) request.get("id"))
					.withString("email", (String) request.get("email"))
					.withBoolean("email_verified", (Boolean) request.get("email_verified"))
					.withString("name", (String) request.get("name"))
					.withString("provider_id", (String) request.get("provider_id"))
					.withString("phone", (String) request.get("phone"))
					.withString("photo", (String) request.get("photo"));
			
			UserTable.putItem(item);
			response.setBody(request.toString());
		
		} catch (Exception ex) {
			ex.printStackTrace();	
			response.setBody(ex.toString());
		}
		
		return response;
	}
}