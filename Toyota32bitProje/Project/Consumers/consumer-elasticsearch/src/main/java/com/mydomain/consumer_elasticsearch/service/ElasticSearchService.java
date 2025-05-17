package com.mydomain.consumer_elasticsearch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydomain.consumer_elasticsearch.config.ConfigReader;
import com.mydomain.consumer_elasticsearch.model.Rate;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ElasticSearchService {

    private final RestHighLevelClient client;
    private final ObjectMapper mapper;
    private final String index = ConfigReader.getEsIndex();

    public boolean indexRate(Rate rate) {
        try {
            IndexRequest req = new IndexRequest(index)
                    .id(rate.getId().toString())
                    .source(mapper.writeValueAsString(rate), XContentType.JSON);

            IndexResponse resp = client.index(req, RequestOptions.DEFAULT);

            boolean ok = resp.getResult() == DocWriteResponse.Result.CREATED
                    || resp.getResult() == DocWriteResponse.Result.UPDATED;

            log.debug("Rate {} indexed → {}", rate.getName(), resp.getResult());
            return ok;

        } catch (Exception e) {
            log.error("Failed to index rate {}", rate.getName(), e);
            return false;
        }
    }
}
