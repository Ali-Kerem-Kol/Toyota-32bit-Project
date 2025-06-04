package com.mydomain.consumer_elasticsearch.service;

import com.mydomain.consumer_elasticsearch.model.Rate;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka'dan gelen PIPE formatlı mesajları parse ederek Rate nesnesine dönüştüren servis.
 */
@Service
@Log4j2
public class DataProcessorService {

    /**
     * Gelen Kafka mesajını parse eder ve Elasticsearch'e yazılabilir hale getirir.
     *
     * @param rawMessage Kafka'dan gelen düz metin mesaj
     * @return Eğer format doğruysa Rate nesnesi, değilse null
     */
    public Rate parseAndConvert(String rawMessage) {
        log.trace("📨 Incoming raw Kafka message: {}", rawMessage);

        String[] parts = rawMessage.split("\\|");
        if (parts.length != 4) {
            log.warn("⚠️ Malformed PIPE message (expected 4 fields): {}", rawMessage);
            return null;
        }

        try {
            String rateName = parts[0];
            double bid = Double.parseDouble(parts[1]);
            double ask = Double.parseDouble(parts[2]);
            long timestamp = Instant.parse(parts[3]).toEpochMilli();

            Rate rate = Rate.builder()
                    .id(UUID.randomUUID())
                    .name(rateName)
                    .bid(bid)
                    .ask(ask)
                    .timestamp(timestamp)
                    .build();

            log.debug("✅ Parsed message → name={}, bid={}, ask={}, timestamp={}",
                    rateName, bid, ask, timestamp);

            return rate;

        } catch (NumberFormatException e) {
            log.warn("🚫 Invalid bid/ask number format in message → {}", rawMessage);
        } catch (Exception e) {
            log.error("❌ Unexpected error while parsing message → {} → {}", rawMessage, e.getMessage(), e);
        }

        return null;
    }
}