package test;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.AbstractUpdateRequest;
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest;
import org.apache.solr.client.solrj.response.QueryResponse;

import java.io.File;
import java.io.IOException;

public class IndexPDFTest {
	public static void main(String[] args) {
		try {
			String fileName = "c:/solr-word.pdf";
			String solrId = "solr-word.pdf";

			indexPDF(fileName, solrId);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 索引PDF文件
	 * @param fileName
	 * @param solrId
	 * @throws IOException
	 * @throws SolrServerException
	 */
	public static void indexPDF(String fileName, String solrId)
			throws IOException, SolrServerException {

		String urlString = "http://localhost:8080/solr/core1";
		SolrClient client = new HttpSolrClient(urlString);

		ContentStreamUpdateRequest up = new ContentStreamUpdateRequest(
				"/update/extract");

		up.addFile(new File(fileName),"application/pdf");

		up.setParam("literal.id", solrId);
		up.setParam("uprefix", "attr_");
		up.setParam("fmap.content", "attr_content");

		up.setAction(AbstractUpdateRequest.ACTION.COMMIT, true, true);

		client.request(up);

		QueryResponse rsp = client.query(new SolrQuery("pdf"));

		System.out.println(rsp);
	}
}
