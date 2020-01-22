package model;

import java.util.ArrayList;

public class Media {
	int total;
	int total_page;
	int per_page;
	ArrayList<Content> results;
	
	public int getTotal() {
		return total;
	}
	public void setTotal(int total) {
		this.total = total;
	}
	public int getTotal_page() {
		return total_page;
	}
	public void setTotal_page(int total_page) {
		this.total_page = total_page;
	}
	public int getPer_page() {
		return per_page;
	}
	public void setPer_page(int per_page) {
		this.per_page = per_page;
	}
	public ArrayList<Content> getResults() {
		return results;
	}
	public void setResults(ArrayList<Content> results) {
		this.results = results;
	}
	
	
}
