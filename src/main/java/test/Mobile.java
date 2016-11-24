package test;

import org.apache.solr.client.solrj.beans.Field;

import java.util.Date;

public class Mobile {
	@Field
	private long id;
	@Field
	private String productName;
	@Field
	private String color;
	@Field
	private double price;
	@Field
	private String brand;
	@Field
	private String wlan;
	@Field("arrive_date")
	private Date arriveDate;
	public long getId() {
		return id;
	}
	public void setId(long id) {
		this.id = id;
	}
	public String getProductName() {
		return productName;
	}
	public void setProductName(String productName) {
		this.productName = productName;
	}
	public String getColor() {
		return color;
	}
	public void setColor(String color) {
		this.color = color;
	}
	public double getPrice() {
		return price;
	}
	public void setPrice(double price) {
		this.price = price;
	}
	public String getBrand() {
		return brand;
	}
	public void setBrand(String brand) {
		this.brand = brand;
	}
	public String getWlan() {
		return wlan;
	}
	public void setWlan(String wlan) {
		this.wlan = wlan;
	}
	public Date getArriveDate() {
		return arriveDate;
	}
	public void setArriveDate(Date arriveDate) {
		this.arriveDate = arriveDate;
	}
}
