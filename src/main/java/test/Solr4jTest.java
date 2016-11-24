package test;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.UpdateResponse;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Solr4J测试类
 * @author Lanxiaowei
 *
 */
public class Solr4jTest {
	public static void main(String[] args) throws Exception {
		SolrClient client = new HttpSolrClient("http://localhost:8080/solr/core2");
		/*QueryResponse resp = client.query("core2", new SolrQuery("productName:iphone").setParam("rows", Integer.MAX_VALUE + ""));
		SolrInputDocument document = new SolrInputDocument();
		document.addField("productName", "苹果6 plus",2.0f);
		client.add(document);
		client.commit();
		
		System.out.println(resp.toString());
		SolrDocumentList list = resp.getResults();
		for (SolrDocument solrDocument : list) {
			String productName = solrDocument.get("productName").toString();
			System.out.println(productName);
		}*/
		//System.setProperty("date.timezone", "UTC");
		String dateStr = "2015-09-07";
		DateFormat format1 = new SimpleDateFormat("yyyy-MM-dd");
		Date date = format1.parse(dateStr);
		Mobile m1 = new Mobile();
		m1.setId(1111111);
		m1.setProductName("苹果6plus");
		m1.setBrand("iphone");
		m1.setColor("土豪金");
		m1.setPrice(6200d);
		m1.setWlan("移动4G");
		m1.setArriveDate(date);

		UpdateResponse response = client.addBean(m1);
		System.out.println(response.toString());
	}
}
