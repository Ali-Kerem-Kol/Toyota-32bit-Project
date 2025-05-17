package com.mydomain.consumer_elasticsearch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydomain.consumer_elasticsearch.config.ConfigReader;
import com.mydomain.consumer_elasticsearch.model.Rate;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka topic'inden gelen JSON mesajları alır,
 * Rate nesnesine dönüştürür ve Elasticsearch'e yazar.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class KafkaConsumerService {

    private final ObjectMapper mapper;
    private final ElasticSearchService esService;

    /**
     * Dinleyici:
     *  - topic adı config.json'dan okunur
     *  - key ve value String deserializer kullanıyoruz
     */
    @KafkaListener(
            topics = "#{T(com.mydomain.consumer_elasticsearch.config.ConfigReader).getKafkaTopic()}",
            groupId = "#{T(com.mydomain.consumer_elasticsearch.config.ConfigReader).getKafkaGroupId()}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record) {

        String msg = record.value();
        try {
            Rate rate;

            if (msg.contains("|")) {                      // ---- PIPE FORMAT ----
                // PF1_USDTRY|33.60|35.90|2024-12-16T16:07:15.504
                String[] p = msg.split("\\|");
                if (p.length != 4) throw new IllegalArgumentException("Bad pipe msg");

                rate = Rate.builder()
                        .id(UUID.randomUUID())
                        .name(p[0])
                        .bid(Double.parseDouble(p[1]))
                        .ask(Double.parseDouble(p[2]))
                        .timestamp(Instant.parse(p[3]).toEpochMilli())
                        .build();
            } else {                                     // ---- JSON FORMAT ----
                rate = mapper.readValue(msg, Rate.class);
            }

            esService.indexRate(rate);

        } catch (Exception e) {
            log.error("Bad message @ offset {} – {}", record.offset(), e.getMessage());
        }
    }
}
