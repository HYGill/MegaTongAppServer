package model;

import java.util.ArrayList;

public class Content {
	String id;
	long created_at;
	long updated_at;
	int likes;
	int ranking;
	String media;
	String description;
	User user;
	String video;
	PhotoContent photoContent;
	//ArrayList<PhotoInfo> photos;
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public long getCreated_at() {
		return created_at;
	}
	public void setCreated_at(long created_at) {
		this.created_at = created_at;
	}
	public long getUpdated_at() {
		return updated_at;
	}
	public void setUpdated_at(long updated_at) {
		this.updated_at = updated_at;
	}
	public int getLikes() {
		return likes;
	}
	public void setLikes(int likes) {
		this.likes = likes;
	}
	public int getRanking() {
		return ranking;
	}
	public void setRanking(int ranking) {
		this.ranking = ranking;
	}
	public String getMedia() {
		return media;
	}
	public void setMedia(String media) {
		this.media = media;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public User getUser() {
		return user;
	}
	public void setUser(User user) {
		this.user = user;
	}
	public String getVideo() {
		return video;
	}
	public void setVideo(String video) {
		this.video = video;
	}
/*	public ArrayList<PhotoInfo> getPhotos() {
		return photos;
	}
	public void setPhotos(ArrayList<PhotoInfo> photos) {
		this.photos = photos;
	}*/
	public PhotoContent getPhotoContent() {
		return photoContent;
	}
	public void setPhotoContent(PhotoContent photoContent) {
		this.photoContent = photoContent;
	}
	
	
}
