'use strict';

const Busboy = require('busboy');
const sha1 = require('sha1');
const uuid = require('uuid4');

const AWS = require('aws-sdk');

const BUCKET_NAME = '';
const IAM_USER_KEY = '';
const IAM_USER_SECRET = '';

const parse = (event) => new Promise((resolve, reject) => {
	const busboy = new Busboy({
		headers: {
			'Content-Type': event.params.header["Content-Type"] || event.params.header["content-type"] 
		}
	});
	const result = {
			files: []
	};

	const feedId = uuid();
	busboy.on('file', (fieldname, file, filename, encoding, mimetype) => {
		const uploadFile = {};

		file.on('data', data => {
			uploadFile.content = data;

			let s3bucket = new AWS.S3({
				accessKeyId: IAM_USER_KEY,
				secretAccessKey: IAM_USER_SECRET,
				Bucket: BUCKET_NAME
			});
			var params = {
					Bucket: BUCKET_NAME + "/" + result["userId"] + "/" + feedId,
					Key: filename,
					Body: data
			};
			s3bucket.putObject(params, function (err, data) {
				if (err) {
					console.log('error in callback');
					console.log(err);
				}
				console.log('success');
				console.log(data);
			});
		});

		file.on('end', () => {
			if (uploadFile.content) {
				uploadFile.filename = filename;
				uploadFile.contentType = mimetype;
				uploadFile.encoding = encoding;
				uploadFile.fieldname = fieldname;
				result.files.push(uploadFile);
			}
		});
	});

	busboy.on('field', (fieldname, value) => {
		result[fieldname] = value;
		result['feedId'] = feedId;
	});

	busboy.on('error', error => {
		reject(error);
	});

	busboy.on('finish', () => {
		resolve(result);
	});

	busboy.write(event["body-json"], 'base64');
	busboy.end();
});
module.exports.parse = parse;
