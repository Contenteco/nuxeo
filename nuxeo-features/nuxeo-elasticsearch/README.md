nuxeo-elasticsearch
===================

## About

This project aims at providing Nuxeo bundles to integrate
Elasticsearch with the Nuxeo Platform.

The idea is to index/query Nuxeo Documents to/from Elasticsearch.

## Building

To build and run the tests, simply start the Maven build:

    mvn clean install

## Elasticsearch configuration

The default configuration uses an embedded Elasticsearch instance
which is running in the same JVM as the Nuxeo Platform. By default the
Elasticsearch indexes will be located in
`nxserver/data/elasticsearch`.

This embedded mode **is only for testing purpose** and should not be used
in production.

For production you need setup an Elasticsearch cluster version 1.1.2.
To join the cluster edit the `nuxeo.conf` and add the following lines:

    elasticsearch.addressList=somenode:9300,anothernode:9300
    elasticsearch.clusterName=elasticsearch


Where:

- `addressList` points to one or many Elasticsearch nodes. Note that we
   connect to the API port **9300** and not the http port 9200.

- `clusterName` is the cluster name to join, `elasticsearch` being the
   default cluster name of a debian package.

You can find all the available options in the [nuxeo.defaults](https://github.com/nuxeo/nuxeo-distribution/blob/master/nuxeo-distribution-resources/src/main/resources/templates-tomcat/common-base/nuxeo.defaults).


## Reindexing

When upgrading or changing the Elasticsearch configuration you need to index the content

     1. Go to Admin Center > Elasticsearch > Admin

     2. Use the ReIndex button
        This is an asynchron job, you can see in the Admin Center > Elasticsearch > Info
        when there is no more indexing activity.


## Change the Elasticsearch settings or mapping of an existing instance

If you want to change the settings or the mapping you need to reindex
all the Elasticsearch documents, here is the fast way to achieve this
without extracting the data from Nuxeo and possibily with 0 downtime.

### Requirements

- Java 7 and [stream2es](https://github.com/elasticsearch/stream2es/)

        curl -O download.elasticsearch.org/stream2es/stream2es; chmod +x stream2es

- An http access (port 9200) to the Elasticsearch, for an embedded
  instance you need to set the `elasticsearch.httpEnabled=true` option
  in the `nuxeo.conf` file.

### Extract the configuration

Using the [`es_get_conf.sh` script](https://github.com/nuxeo/nuxeo-features/blob/master/nuxeo-elasticsearch/scripts/es_get_conf.sh)

        ./es_get_conf.sh localhost:9200/nuxeo

  Output:

        ### Dump settings
        ### Dump mapping
        ### Merging conf
        ### Done
        /tmp/es-conf.json

  This small script does nothing more than merging the settings and
  mapping configuration into a single file.

### Modify the setting or mapping

Edit the `/tmp/es-conf.json` file at your convenance.

Here are some examples of common changes

##### Customize the language

The Nuxeo code uses a fulltext analyzer named `fulltext`, this is an
alias that point to the `en_fulltext` analyzer by default. By moving
the

     "alias" : "fulltext",

line into the `fr_fulltext` you setup a French analyzer.


##### Make ILIKE works (Case insensitive search)

If you want to do case insensitive search or use an `ILIKE` operation:

Add an analyzer in the `analyzers` section:

            "lowercase" : {
              "type" : "custom",
              "filter" : [ "lowercase", "asciifolding" ],
              "tokenizer" : "keyword"
            },

Then add the analyzer to your field in the `mappings` section for
instance:

          "my:field" : {
            "type" : "string",
            "analyzer" : "lowercase"
          },

##### New fulltext field

To use the fulltext search syntax on a custom field you need to create
a multi_field like this:

           "my:text" : {
              "type" : "multi_field",
              "fields" : {
                 "my:text" : {
                   "index" : "no",
                   "include_in_all" : "true",
                   "type" : "string"
                 },
                 "fulltext" : {
                   "type": "string",
                   "analyzer" : "fulltext"
                 }
               }
            },

##### Exclude a field from the fulltext search

Suppose you want to exclude `my:secret` field from the `ecm:fulltext` search:


       "my:secret" : {
          "type" : "string",
          "include_in_all" : false,
       },



### Create a new index

With the new configuration:

        curl -XPUT localhost:9200/nuxeo-new -d @/tmp/es-conf.json

  Output

        {"acknowledged":true}

### Copy the documents to the new index:


        stream2es es --source http://localhost:9200/nuxeo --target http://localhost:9200/nuxeo-new

  Output

        stream es from http://localhost:9200/nuxeo to http://localhost:9200/nuxeo-new
        00:00,993 396,8d/s 1042,0K/s 394 394 1059563 0 5f959f21-5e02-4346-acae-a56614224058
        00:01,276 848,0d/s 1628,8K/s 1082 688 1068667 0 24decc6a-2fb7-4c9d-a64b-4fc1f88c707f
        00:01,497 1181,0d/s 2085,1K/s 1768 686 1068139 0 cb972a9d-cf6a-45e7-be27-c79c7c5630f0
        00:01,708 1436,2d/s 2438,4K/s 2453 685 1068381 0 abbec188-9808-4134-861e-78bab63c4e78
        00:01,931 1626,1d/s 2697,2K/s 3140 687 1068564 0 d3e98084-66cf-442d-a708-4b5e39d86f9b
        flushing index queue
        00:02,180 1755,5d/s 2867,6K/s 3827 687 1068080 0 9fec1fbe-7c4b-4a84-b6d7-a774e54916bf
        00:02,219 1872,9d/s 3042,2K/s 4156 329 511349 0 62c56520-2d26-4778-8292-f91a099f65f8
        streamed 4156 indexed 4156 bytes xfer 6912743 errors 0


### Plug Nuxeo to the new index

Update the `elasticsearch.indexName` value in the `nuxeo.conf` and restart Nuxeo.
You can delete your old index if you are happy with the new one.


### 0 downtime

You can also do this without any downtime using an
[alias](http://www.elasticsearch.org/guide/en/elasticsearch/reference/current/indices-aliases.html).

The Nuxeo will only know the `nuxeo` alias and once your mapping is
ready on `nuxeo_v2` you switch atomically

    curl -XPOST 'localhost:9200/_aliases' -d '{
      "actions" : [
          { "remove" : { "index" : "nuxeo_v1", "alias" : "nuxeo" } },
          { "add" : { "index" : "nuxeo_v2", "alias" : "nuxeo" } }
      ]
    }'



## Limitations

- ACL filtering works only on simplified ACL. We don't support
  negative ACE other than `Deny Everyone`. Documents with negative ACL
  will not be accessible for non administrator accounts.

  This simplified ACL is the default mode since 5.9.6 visit
  [NXP-14045](https://jira.nuxeo.com/browse/NXP-14045) for more
  information.

- NXQL Limitations:

  - Fulltext

      - The fulltext syntax accepted is different from the default
        NXQL.  It is the Elasticsearch [simple query string
        syntax](http://www.elasticsearch.org/guide/en/elasticsearch/reference/current/query-dsl-simple-query-string-query.html#_simple_query_string_syntax).

      - The `ecm:fulltext` matches the Elasticsearch `_all` field
        which is the concatenation of all fields. This is different
        from the NXQL `ecm:fulltext` which matches only some explicit
        fields. You can adapt the mapping to exclude some field see
        above section for example.

      - Custom fulltext indexes are not supported.
        `ecm:fulltext_someindex` will match the `_all` field.

      - Fulltext search on field is supported if the Elasticsearch
        mapping is properly defined. For instance
	`ecm:fulltext.dc:title = 'foo*'` works if there is a multi
	field `dc:title.fulltext`. See above section for example.

  - Fields are case-sensitive: `ecm:primaryType` vs `ecm:primarytype`

  - The is no select clause support, so NO: DISTINCT, COUNT, AVERAGE,
    MAX, MIN, operators like: + - / *.

  - ILIKE will work only with a proper mapping with an analyzer
    configured to lowercase index.

  - There is no support of complex list correlations or indices support
    we always search on all elements so this kind of query can not be done:

        files:files/*1/file/name LIKE '%.txt' AND files:files/*1/file/length = 0
        dc:subjects/3 = 'foo'

    These previous queries will not match any documents. Note that the
    match any `/*` is supported.

  - No support of canonical name for complex properties without
    prefix, so you need to write `file:content/name` and not just
    `content/name`.  See
    [NXP-15426](https://jira.nuxeo.com/browse/NXP-15426) for more
    information.


## Reporting Problems

To understand why a document is not present in search results or not
indexed, you can activate a debug trace, look at the `lib/log4j.xml`
file and uncomment the ELASTIC section:

      <appender name="ELASTIC" class="org.apache.log4j.FileAppender">
        <errorHandler class="org.apache.log4j.helpers.OnlyOnceErrorHandler" />
        <param name="File" value="${nuxeo.log.dir}/elastic.log" />
        <param name="Append" value="false" />
        <layout class="org.apache.log4j.PatternLayout">
          <param name="ConversionPattern" value="%d{ISO8601} %-5p [%t][%c] %m%X%n" />
        </layout>
      </appender>

      <category name="org.nuxeo.elasticsearch" additivity="false">
        <priority value="TRACE" />
        <appender-ref ref="ELASTIC" />
      </category>

The `elastic.log` will contain all the requests done by Nuxeo to
Elasticsearch including the `curl` command ready to be copied/pasted in a
term.

If you run in default embedded mode you need to enable the HTTP access
to perform request, just add `elasticsearch.httpEnabled=true` in your
`nuxeo.conf`. Note that this is only for debug purpose you should never
ever expose publicly the Elasticsearch ports.


## Status

Check the ongoing development [check the jira tikets](https://jira.nuxeo.com/issues/?jql=project%20%3D%20NXP%20AND%20component%20%3D%20Elasticsearch%20AND%20Status%20!%3D%20%22Resolved%22%20ORDER%20BY%20updated%20DESC%2C%20priority%20DESC%2C%20created%20ASC)
