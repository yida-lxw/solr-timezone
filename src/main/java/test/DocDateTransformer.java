package test;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.response.transform.DocTransformer;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Lanxiaowei
 * Craated on 2016/11/24 12:38
 */
public class DocDateTransformer extends DocTransformer {
    private String format = "yyyy-MM-dd HH:mm:ss";
    private String tz = "Asia/Shanghai";
    private String name;

    public DocDateTransformer() {}

    public DocDateTransformer(String format) {
        this.format = format;
    }

    public DocDateTransformer(String format,String tz) {
        this.format = format;
        this.tz = tz;
    }

    public DocDateTransformer(String format,String tz,String name) {
        this.format = format;
        this.tz = tz;
        this.name = name;
    }

    @Override
    public String getName() {
        if(null == name || "".equals(name)) {
            return "DocDateTransformer";
        }
        return name;
    }

    @Override
    public void transform(SolrDocument solrDocument, int i, float v) throws IOException {
        Collection<String> fields = solrDocument.getFieldNames();
        if(null == fields || fields.size() <= 0) {
            return;
        }
        for(String field : fields) {
            Object value = solrDocument.getFieldValue(field);
            if(null == value) {
                continue;
            }
            if(value instanceof Date) {
                String val = DateUtils.parseDate((Date)value,format);
                solrDocument.addField(field + "_date",val);
            } else if(value instanceof String) {
                if(isUTCDateString(value.toString())) {
                    value = DateUtils.string2Timezone("yyyy-MM-dd'T'HH:mm:ss'Z'",
                            value.toString(),format,tz);
                    solrDocument.setField(field,value);
                }
            }
        }
    }

    /**
     * 判断是否为UTC日期时间格式
     * @param dateString
     * @return
     */
    public boolean isUTCDateString(String dateString) {
        Pattern pattern = Pattern.compile("\\d{4}-\\d{1,2}-\\d{1,2}T\\d{1,2}:\\d{1,2}:\\d{1,2}Z");
        Matcher matcher = pattern.matcher(dateString);
        return matcher.matches();
    }
}
