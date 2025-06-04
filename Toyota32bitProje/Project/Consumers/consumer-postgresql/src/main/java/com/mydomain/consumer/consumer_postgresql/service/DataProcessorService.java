package com.mydomain.consumer.consumer_postgresql.service;

import com.mydomain.consumer.consumer_postgresql.model.TblRates;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Kafka'dan gelen mesajları parse edip TblRates nesnesine dönüştüren servis sınıfı.
 */
@Service
@Log4j2
public class DataProcessorService {

    /**
     * Gelen Kafka mesajını parse eder ve veritabanına yazılabilir hale getirir.
     *
     * @param rawMessage Kafka'dan gelen düz metin mesaj
     * @return Eğer format doğruysa TblRates nesnesi, değilse null
     */
    public TblRates parseAndConvert(String rawMessage) {
        log.trace("📨 Incoming raw Kafka message: {}", rawMessage);

        String[] parts = rawMessage.split("\\|");
        if (parts.length < 4) {
            log.warn("⚠️ Malformed message (missing fields): {}", rawMessage);
            return null;
        }

        try {
            String rateName = parts[0];
            double bid = Double.parseDouble(parts[1]);
            double ask = Double.parseDouble(parts[2]);
            String isoTimestamp = parts[3];

            OffsetDateTime odt = OffsetDateTime.parse(isoTimestamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            LocalDateTime istanbulTime = odt.atZoneSameInstant(ZoneId.of("Europe/Istanbul")).toLocalDateTime();

            TblRates entity = new TblRates();
            entity.setRateName(rateName);
            entity.setBid(bid);
            entity.setAsk(ask);
            entity.setRateUpdateTime(istanbulTime);
            entity.setDbUpdateTime(LocalDateTime.now());

            log.debug("✅ Parsed message successfully → rateName={}, bid={}, ask={}, time={}",
                    rateName, bid, ask, istanbulTime);

            return entity;

        } catch (NumberFormatException e) {
            log.warn("🚫 Failed to parse bid/ask as numbers → {}", rawMessage);
        } catch (Exception e) {
            log.error("❌ Unexpected error while parsing message: {} → {}", rawMessage, e.getMessage(), e);
        }

        return null;
    }
}