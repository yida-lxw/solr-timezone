# solr-timezone
This project was build for addressing the solr timezone issue.May be it had bother you for a long time,but at this point,let it go,you can use this by anyway in your application to escape away from that issue.



== Support

* Solr 6.2.1  
* JDK1.8+

== Prepare

=== Import jar as following:

** solr-6.2.1\dist\solr-solrj-6.2.1.jar  

** solr-6.2.1\dist\solrj-lib\jackson-*-${version}.jar  

== How to use?

=== config schema.xml:

[source]
<fieldType name="tcndate" class="test.TrieDateField" precisionStep="6" 
    positionIncrementGap="0"/>
<fields>
  <field name="id" type="string" indexed="true" stored="true" />
  <field name="product_name" type="string" indexed="true" stored="true"/>
  <field name="order_date" type="tcndate" indexed="true" stored="true"/>
  <field name="_version_" type="long" indexed="true" stored="true" />
</fields>

=== config solrconfig.xml:

[source]
</requestHandler>
......
<queryResponseWriter name="json" class="test.JSONResponseWriter">
    <str name="content-type">application/json; charset=UTF-8</str>
    <str name="tz">Asia/Shanghai</str>
    <str name="format">yyyy-MM-dd HH:mm:ss</str>
</queryResponseWriter>