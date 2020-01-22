'use strict';

const parser = require('./busboy.js');
const sha1 = require('sha1');

const AWS = require('aws-sdk');
var Content = require('./Content.js');
var dynamoDB = new AWS.DynamoDB.DocumentClient();
var contentsInfo;

exports.handler = function(event, context, callback) {    
	var files = null;

	const eventParser = parser.parse(event)
	.then(function(result){

        var items = [];
        var itemCnt = result.files.length;
        for (var i = 0; i < itemCnt; i++) {
            let accessFile = result.files[i];
            var fileFullPath = '/' + result['userId'] + "/" + result['feedId'] + "/" + accessFile.filename;

            items.push(fileFullPath);
        }

        var createdAt = Date.now();

		var contentsParams = {
				TableName:"content",
				Item:{
                    "organization" : "megazone",
                    "id": result['feedId'],
                    "userId": result["userId"],
                    "fileName" : result.fileName,
					"created_at": createdAt,
					"updated_at": createdAt,
                    "description": result["description"],
                    "photos1" : items[0],
					"photos2" : items[1],
					"photos3" : items[2],
					"photos4" : items[3],
					"likes": 0,
					"ranking": 0,
                    "media": result["media"],
                    "video": "null"
				}
        };
        console.log("contentsParams id : " + contentsParams.Item["id"]);
        var content = Content(contentsParams.Item["id"], contentsParams.Item["created_at"], contentsParams.Item["updated_at"], contentsParams.Item["description"], contentsParams.Item["likes"], contentsParams.Item["ranking"], contentsParams.Item["media"]);
        console.log("typeof : " + typeof(content));
        console.log("contents : " + JSON.stringify(content));

		dynamoDB.put(contentsParams, function(err, data) {
			if (err) {
				console.log("Unable to add item. Error JSON3:" + JSON.stringify(err));
			} else {
				console.log("Added item:" + JSON.stringify(data));
			}
        });

        var createdParam = {
            TableName:"createdIndex",
            Item:{
                "id": result['feedId'],
                "created_at": createdAt
            }
        };
        dynamoDB.put(createdParam, function(err, data) {
            if (err) {
                console.log("Unable to add item. Error JSON2:" + JSON.stringify(err));
            } else {
                console.log("Added created");
            }
        });

        var rankingParam = {
            TableName:"likes-by-organization",
            Item:{
                "organization": "dev",
                "id": result['userId'],
                "likeHash": "00000" + result['feedId']
            }
        };
        dynamoDB.put(rankingParam, function(err, data) {
            if (err) {
                console.log("Unable to add item. Error JSON2:" + JSON.stringify(err));
            } else {
                console.log("Added Ranking");
            }
        });

        var photoContent = {};
        var photos = [];
        items.forEach(function(items){
            photos.push({"id": result['feedId'] + "@" + result['userId'], "photo":items})
        });

        var userInfo;
        
        console.log("userId : " + result['userId']);
        var userParams = {
            TableName: "user",
            Key:{
                "id": result['userId']
            }
        };

        var sendingPromise = new Promise((resolve, reject) => {
            dynamoDB.get(userParams, function(err, data) {
                if (err) {
                    console.error("Unable to read item. Error JSON:" + err);
                } else {
                    photoContent["count"] = itemCnt;
                    photoContent["photos"] = photos;
                    content["user"] = data.Item;
                    content["photoContent"] = photoContent;

                    resolve(content);
                }
            });           
        });

        sendingPromise.then(function(content){
            console.log("finally Content : " + JSON.stringify(content));
            callback(null, content);

        }).catch(function(err){
            console.error("callbackerr:" + err);
        });    
	})
	.catch(function(err){
		console.error("callbackerr2:" + err);
    });
}
