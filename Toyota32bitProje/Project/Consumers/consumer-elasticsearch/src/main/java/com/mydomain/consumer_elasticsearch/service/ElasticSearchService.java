package com.mydomain.consumer_elasticsearch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydomain.consumer_elasticsearch.model.Rate;
import org.apache.http.HttpHost;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;

import java.io.Closeable;
import java.io.IOException;

public class ElasticsearchService implements Closeable {

    private static final Logger logger = LogManager.getLogger(ElasticsearchService.class);

    private final RestHighLevelClient esClient;
    private final ObjectMapper objectMapper;
    private final String indexName;

    public ElasticsearchService(String host, int port, String indexName) {
        this.esClient = new RestHighLevelClient(
                RestClient.builder(new HttpHost(host, port, "http"))
        );
        this.objectMapper = new ObjectMapper();
        this.indexName = indexName;
    }

    public void indexRate(Rate rate) {
        try {
            // Doküman ID
            String docId = rate.getRateName() + "_" + rate.getFields().getTimestamp();

            String json = objectMapper.writeValueAsString(rate);
            IndexRequest request = new IndexRequest(indexName)
                    .id(docId)
                    .source(json, XContentType.JSON);

            IndexResponse response = esClient.index(request, RequestOptions.DEFAULT);
            logger.info("Indexed rate => id={}, result={}", docId, response.getResult());
        } catch (Exception e) {
            logger.error("Error indexing rate: {}", e.getMessage(), e);
        }
    }

    @Override
    public void close() throws IOException {
        // Kaynakları serbest bırak
        try {
            esClient.close();
        } catch (Exception e) {
            logger.warn("Error closing ES client => {}", e.getMessage());
        }
    }
}
