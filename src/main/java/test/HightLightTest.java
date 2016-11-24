package test;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.ORDER;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.RangeFacet;
import org.apache.solr.client.solrj.response.RangeFacet.Count;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class HightLightTest {
	public static void main(String[] args) throws Exception {
		String url = "http://localhost:8080/solr/core2";
		SolrClient client = new HttpSolrClient(url);
		SolrQuery query = new SolrQuery("*:*");
		//开启高亮
		query.setHighlight(true);
		query.addHighlightField("productName");
		query.setHighlightFragsize(100);
		query.setHighlightSimplePre("<span style=\"color:red;\">");
		query.setHighlightSimplePost("</span>");
		//FastVectorHightLighter: 不是位置偏移量 基于项向量
		
		//write type: json/xml,default json
		query.setParam("wt", "xml");
		
		//tz: timezone  默认UTC
		query.setParam("tz", "Asia/Shanghai");
		
		//排序
		//query.addSort(new SortClause("price", ORDER.desc));
		query.addSort("price", ORDER.desc);
		
		//分页 start = (currentPage - 1) * pageSize
		//currentPage:当前页码，从1开始计算
		query.setStart(0);
		// rows就相当于pageSize
		query.setRows(10);
		
		//开启Facet Query
		query.setFacet(true);
		query.addFacetField("price");
		//query.addFacetQuery("price:[1000  TO 2000]");
		query.addNumericRangeFacet("price", 1000, 6000, 1000);
		
		QueryResponse response = client.query(query);
		
		/****************查询返回文档打印begin***********/
		SolrDocumentList docList = response.getResults();
		Iterator<SolrDocument> it = docList.listIterator();
		while(it.hasNext()) {
			SolrDocument doc = it.next();
			System.out.println("id:" + doc.get("id"));
			System.out.println("productName:" + doc.get("productName"));
			System.out.println("price:" + doc.get("price"));
			System.out.println("brand:" + doc.get("brand"));
			System.out.println("wlan:" + doc.get("wlan"));
			System.out.println("///////////////////////////////////");
		}
		/****************查询返回文档打印end***********/
		
		/****************高亮信息打印begin***********/
		Map<String,Map<String,List<String>>> hightlists = response.getHighlighting();
		for(Map.Entry<String, Map<String,List<String>>> entry : hightlists.entrySet()) {
			String key = entry.getKey();
			System.out.println("id:" + key);
			Map<String,List<String>> map = entry.getValue();
			for(Map.Entry<String,List<String>> ent : map.entrySet()) {
				String k = ent.getKey();
				List<String> lt = ent.getValue();
				System.out.println(k + ":" + join(lt));
			}
		}
		/****************高亮信息打印end***********/
		
		/****************Facet维度查询信息打印end***********/
		/*List<FacetField> facetFields = response.getFacetFields();
		for(FacetField facetField : facetFields) {
			String name = facetField.getName();
			int count = facetField.getValueCount();
			System.out.println(name + "-->" + count);
		}*/
		
		List<RangeFacet> rangeFacets = response.getFacetRanges();
		for(RangeFacet rangeFacet : rangeFacets) {
			String field = rangeFacet.getName();
			//int before = rangeFacet.getBefore().intValue();
			//int after = rangeFacet.getAfter().intValue();
			List<Count> list = rangeFacet.getCounts();
			for(Count facetCount : list) {
				int count = facetCount.getCount();
				String value = facetCount.getValue();
				System.out.println(value + ":" + count);
			}
			System.out.println("field:" + field);
			//System.out.println("[" + before + "-" + after + "]:" + "");
		}
		/****************Facet维度信息打印end***********/
	}
	
	public static String join(Collection<String> coll) {
		StringBuilder builder = new StringBuilder();
		for(String s : coll) {
			builder.append(s).append(",");
		}
		return builder.toString().replaceAll(",$", "");
	}
}
