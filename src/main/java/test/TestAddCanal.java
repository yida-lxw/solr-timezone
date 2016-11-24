package test;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.core.SolrCore;
import org.apache.solr.response.QueryResponseWriter;

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;

/**
 * Created by Lanxiaowei
 * Craated on 2016/11/23 12:52
 */
public class TestAddCanal {
    private static final String SOLRPEDIA_INSTANT_CORE = "http://192.168.1.100:8080/solr/canal";
    /**
     * Zookeeper集群节点，多个使用逗号分割
     */
    private static final String ZK_HOST = "linux.yida01.com:2181,linux.yida02.com:2181,linux.yida03.com:2181";
    private static final String COLLECTION_NAME = "canal";

    public static void main(String[] args) throws Exception {
        //delete();
        add();
        //query();
    }

    public static void query() throws IOException, SolrServerException {
        HttpSolrClient client = new HttpSolrClient(SOLRPEDIA_INSTANT_CORE);
        org.apache.solr.client.solrj.SolrQuery query = new org.apache.solr.client.solrj.SolrQuery();
        query.setRequestHandler("/select");
        query.set("q", "*:*");
        query.set("fl", "id,product_name,order_date");
        query.set("indent","true");
        QueryResponse response = client.query(query, SolrRequest.METHOD.GET);

        SolrDocumentList docList = response.getResults();
        if(null == docList) {
            return;
        }
        for (SolrDocument doc : docList) {
            String id = doc.getFieldValue("id").toString();
            String productName = doc.getFieldValue("product_name").toString();
            String orderDate = doc.getFieldValue("order_date").toString();
            System.out.println("/******************************************************/");
            System.out.println("id:" + id);
            System.out.println("productName:" + productName);
            System.out.println("orderDate:" + orderDate);
            System.out.println("/******************************************************/\n");
        }

        System.out.println("\n以下是普通select查询请求的响应信息：\n");
        System.out.println(response.toString());
    }

    public static void add() throws IOException, SolrServerException, ParseException {
        long start = System.currentTimeMillis();
        int totalDocument = 100;
        /*CloudSolrClient client = createCloudSolrClient(ZK_HOST,COLLECTION_NAME);
        client.connect();*/

        SolrClient client = new ConcurrentUpdateSolrClient(SOLRPEDIA_INSTANT_CORE,10,5);

        String dateStr = "2016-11-24 10:32:59";
        Date order_date = DateUtils.stringToDate(dateStr,"yyyy-MM-dd HH:mm:ss");
        for(int i=0; i < totalDocument; i++) {
            SolrInputDocument doc = new SolrInputDocument();
            doc.addField("id",(i + 1) + "");
            doc.addField("product_name","product_" + (i+1));
            Date orderDate = DateUtils.addHours(order_date,(i + 1));
            doc.addField("order_date",orderDate);
            client.add(doc);
            //addDocWithRetry(client,doc,2);
        }
        //设置为硬提交
        client.commit();
        long end = System.currentTimeMillis();
        System.out.println("add document to SolrCloud have take " + (end - start) + " ms.");
        client.close();
    }

    public static void delete() throws IOException, SolrServerException {
        long start = System.currentTimeMillis();
        int totalDocument = 100;
        CloudSolrClient client = createCloudSolrClient(ZK_HOST,COLLECTION_NAME);
        client.connect();
        client.deleteByQuery(COLLECTION_NAME,"*:*");
        //设置为硬提交
        client.commit();
        long end = System.currentTimeMillis();
        System.out.println("add document to SolrCloud have take " + (end - start) + " ms.");
        client.close();
    }

    public static SolrClient createSolrClient() {
        ConcurrentUpdateSolrClient client = new ConcurrentUpdateSolrClient(SOLRPEDIA_INSTANT_CORE,1,5);
        //client.setRequestWriter(new BinaryRequestWriter());
        return client;
    }

    /**
     * 创建CloudSolrClient实例
     *
     * @param zkHost
     * @return
     */
    public static CloudSolrClient createCloudSolrClient(String zkHost, String defaultCollection) {
        //是否只将索引文档更新请求发送给Shard Leader
        boolean onlySendToLeader = true;
        //指定索引文档属于哪个Collection
        //String defaultCollection = "books";
        //Zookeeper客户端连接Zookeeper集群的超时时间，默认值10000，单位：毫秒
        int zkClientTimeout = 30000;
        //Zookeeper Server端等待客户端成功连接的最大超时时间，默认值10000，单位：毫秒
        int zkConnectTimeout = 30000;
        CloudSolrClient client = new CloudSolrClient(zkHost, onlySendToLeader);
        client.setDefaultCollection(defaultCollection);
        client.setZkClientTimeout(zkClientTimeout);
        client.setZkConnectTimeout(zkConnectTimeout);
        //设置是否并行更新索引文档
        client.setParallelUpdates(true);
        //显式设置索引文档的UniqueKey域，默认值就是id
        client.setIdField("id");
        //设置Collection缓存的存活时间，默认值为1，单位：分钟
        client.setCollectionCacheTTl(2);
        return client;
    }

    /**
     * 添加索引文档并支持自动重试
     * @param client
     * @param doc
     * @param retryInSecs  添加失败后等待retryInSecs秒后自动重试一次，单位：秒
     * @throws Exception
     */
    protected static void addDocWithRetry(CloudSolrClient client, SolrInputDocument doc, int retryInSecs)
            throws Exception {
        try {
            client.add(doc);
        } catch (Exception solrExc) {
            Throwable rootCause = SolrException.getRootCause(solrExc);
            //只有IOException才进行重试
            if (rootCause instanceof IOException) {
                System.out.println(rootCause.getClass().getSimpleName() +
                        " when trying to send a document to SolrCloud, will re-try after waiting " +
                        retryInSecs + " seconds; " + rootCause);
                try {
                    Thread.sleep(retryInSecs * 1000);
                } catch (InterruptedException ignor) {
                }
                // 重试添加索引文档
                client.add(doc);
            }
        }
    }
}
