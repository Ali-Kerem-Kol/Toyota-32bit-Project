package com.mydomain.consumer.consumer_postgresql.service;

import com.mydomain.consumer.consumer_postgresql.model.TblRates;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Kafka topic'inden gelen mesajları dinleyip işleyen servis.
 * Mesaj işleme ve veritabanı kayıt işlemleri başka katmanlara devredilmiştir.
 */
@Service
@Log4j2
public class KafkaConsumerService {

    private final DataProcessorService dataProcessorService;
    private final DatabaseService databaseService;

    public KafkaConsumerService(DataProcessorService dataProcessorService,
                                DatabaseService databaseService) {
        this.dataProcessorService = dataProcessorService;
        this.databaseService = databaseService;
    }

    @KafkaListener(topics = "${spring.kafka.topic}")
    public void consume(ConsumerRecord<String, String> record) {
        String message = record.value();

        log.trace("🔍 Raw Kafka message received → {}", message);

        try {
            log.debug("🔄 Parsing incoming message...");
            TblRates rate = dataProcessorService.parseAndConvert(message);

            if (rate == null) {
                log.warn("⚠️ Message parsing returned null. Skipped → {}", message);
                return;
            }

            log.debug("📌 Parsed message → rateName={}, bid={}, ask={}", rate.getRateName(), rate.getBid(), rate.getAsk());
            log.debug("💽 Attempting to save to database...");
            databaseService.saveRate(rate);

        } catch (Exception e) {
            log.error("❌ Unexpected error during Kafka consumption → {}", e.getMessage(), e);
        }
    }
}