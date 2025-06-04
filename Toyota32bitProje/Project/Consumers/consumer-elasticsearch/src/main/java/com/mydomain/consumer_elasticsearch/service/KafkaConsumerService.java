package com.mydomain.consumer_elasticsearch.service;

import com.mydomain.consumer_elasticsearch.model.Rate;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Kafka'dan gelen mesajları dinler, parse eder ve Elasticsearch'e kaydeder.
 */
@Service
@Log4j2
public class KafkaConsumerService {

    private final DataProcessorService dataProcessorService;
    private final DatabaseService databaseService;

    @Value("${consumer.kafka.topic}")
    private String kafkaTopic;

    public KafkaConsumerService(DataProcessorService dataProcessorService,
                                DatabaseService databaseService) {
        this.dataProcessorService = dataProcessorService;
        this.databaseService = databaseService;
    }

    /**
     * Kafka topic'inden gelen mesajları işler.
     *
     * @param record Kafka mesajı (key, value, partition, offset vs.)
     */
    @KafkaListener(topics = "${consumer.kafka.topic}")
    public void consume(ConsumerRecord<String, String> record) {
        String rawMessage = record.value();

        log.trace("📩 Raw Kafka message received (offset={}, partition={}) → {}",
                record.offset(), record.partition(), rawMessage);

        try {
            log.debug("🔍 Parsing incoming message...");
            Rate rate = dataProcessorService.parseAndConvert(rawMessage);

            if (rate != null) {
                log.debug("💾 Parsed rate: name={}, bid={}, ask={}",
                        rate.getName(), rate.getBid(), rate.getAsk());

                log.debug("📦 Saving rate to Elasticsearch...");
                databaseService.saveRate(rate);
                log.info("✅ Rate saved successfully → {}", rate.getName());
            } else {
                log.warn("⚠️ Message parsing returned null. Skipped → {}", rawMessage);
            }

        } catch (Exception e) {
            log.error("❌ Unexpected error during Kafka consumption → {}", e.getMessage(), e);
        }
    }
}
