package test;

import org.apache.solr.common.params.SolrParams;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.transform.DocTransformer;
import org.apache.solr.response.transform.TransformerFactory;

/**
 * Created by Lanxiaowei
 * Craated on 2016/11/24 12:37
 */
public class DateTransformerFactory extends TransformerFactory {
    @Override
    public DocTransformer create(String s, SolrParams solrParams, SolrQueryRequest solrQueryRequest) {
        String name = solrParams.get("name");
        String format = solrParams.get("format","yyyy-MM-dd HH:mm:ss");
        String tz = solrParams.get("tz","Asia/Shanghai");
        return new DocDateTransformer(format,tz,name);
    }
}
