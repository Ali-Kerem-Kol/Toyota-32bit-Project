package com.mydomain.consumer_elasticsearch.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydomain.consumer_elasticsearch.config.ConfigReader;
import com.mydomain.consumer_elasticsearch.model.Rate;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ElasticSearchService {

    private final ElasticsearchClient client;
    private final ObjectMapper mapper;
    private final String index = ConfigReader.getEsIndex();

    public boolean indexRate(Rate rate) {
        try {
            var resp = client.index(i -> i
                    .index(index)
                    .id(rate.getId().toString())
                    .document(rate)
            );
            String result = resp.result().name();
            boolean ok = result.equalsIgnoreCase("Created")
                    || result.equalsIgnoreCase("Updated");

            if (ok) {
                log.info("Indexed rate {} into ES index '{}' (result={})",
                        rate.getName(), index, result);
            } else {
                log.warn("Indexing returned '{}' for rate {} — check mapping/settings",
                        result, rate.getName());
            }
            return ok;

        } catch (Exception e) {
            log.error("Failed to index rate {}", rate.getName(), e);
            return false;
        }
    }

}
