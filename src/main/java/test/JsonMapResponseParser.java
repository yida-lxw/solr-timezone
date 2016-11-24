package test;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.solr.client.solrj.ResponseParser;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.util.NamedList;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;

/**
 * Created by Lanxiaowei
 * Craated on 2016/11/24 11:52
 * Solr查询响应结果集的JSON转换器
 */
public class JsonMapResponseParser extends ResponseParser {
    private String format = "yyyy-MM-dd HH:mm:ss";

    public JsonMapResponseParser() {}

    public JsonMapResponseParser(String format) {
        this.format = format;
    }

    @Override
    public String getWriterType() {
        return "json";
    }

    @Override
    public NamedList<Object> processResponse(InputStream body, String encoding) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.getSerializationConfig().with(new SimpleDateFormat(format, Locale.getDefault()));
        Map map = null;
        try {
            map = mapper.readValue(body, Map.class);
        } catch (IOException e) {
            throw new SolrException(SolrException.ErrorCode.SERVER_ERROR,
                    "parsing error", e);
        }
        NamedList<Object> list = new NamedList<Object>();
        list.addAll(map);
        return list;
    }

    @Override
    public NamedList<Object> processResponse(Reader reader) {
        throw new RuntimeException("Cannot handle character stream");
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

    @Override
    public String getVersion() {
        return "1";
    }
}
