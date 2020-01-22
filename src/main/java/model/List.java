package model;

import java.util.ArrayList;

public class List {
	int total;
	int pages;
	int perPage;
	ArrayList<Content> results;

	public int getTotal() {
		return this.total;
	}

	public int getPages() {
		return pages;
	}

	public void setPages(int pages) {
		this.pages = pages;
	}

	public int getPerPage() {
		return perPage;
	}

	public void setPerPage(int perPage) {
		this.perPage = perPage;
	}

	public ArrayList<Content> getResults() {
		return this.results;
	}

	public void setTotal(int total) {
		this.total = total;
	}

	public void setResults(ArrayList<Content> results) {
		this.results = results;
	}

}
