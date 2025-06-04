package com.mydomain.consumer_elasticsearch.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.mydomain.consumer_elasticsearch.model.Rate;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Rate nesnelerini Elasticsearch'e kaydeden servis.
 */
@Service
@Log4j2
public class DatabaseService {

    private final ElasticsearchClient client;

    @Value("${spring.elasticsearch.index}")
    private String index;

    public DatabaseService(ElasticsearchClient client) {
        this.client = client;
    }

    /**
     * Verilen Rate nesnesini Elasticsearch'e kaydeder.
     *
     * @param rate Kaydedilecek veri
     */
    public void saveRate(Rate rate) {
        try {
            log.debug("📤 Indexing rate into Elasticsearch → name={}, bid={}, ask={}, timestamp={}",
                    rate.getName(), rate.getBid(), rate.getAsk(), rate.getTimestamp());

            var resp = client.index(i -> i
                    .index(index)
                    .id(rate.getId().toString())
                    .document(rate)
            );

            String result = resp.result().name();

            if (result.equalsIgnoreCase("Created") || result.equalsIgnoreCase("Updated")) {
                log.info("✅ Indexed rate '{}' into Elasticsearch (result: {})", rate.getName(), result);
            } else {
                log.warn("⚠️ Unexpected ES indexing result: '{}' for rate '{}'", result, rate.getName());
            }

        } catch (Exception e) {
            log.error("❌ Failed to index rate into Elasticsearch → {} → rate: {}", e.getMessage(), rate, e);
        }
    }
}