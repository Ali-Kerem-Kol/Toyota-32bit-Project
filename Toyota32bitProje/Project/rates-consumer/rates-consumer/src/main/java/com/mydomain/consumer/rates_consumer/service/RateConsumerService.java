package com.mydomain.consumer.rates_consumer.service;

import com.mydomain.consumer.rates_consumer.model.TblRates;
import com.mydomain.consumer.rates_consumer.repository.RatesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class RateConsumerService {

    @Autowired
    private RatesRepository ratesRepository;

    @KafkaListener(topics = "rates-topic", groupId = "ratesConsumerGroup")
    public void consumeRateMessage(String message) {
        // Beklenen format: "rateName|bid|ask|timestamp(ISO8601)"
        String[] parts = message.split("\\|");
        if (parts.length < 4) {
            System.err.println("❌ Invalid message format, skipping: " + message);
            return;
        }

        String rateName = parts[0];
        double bid;
        double ask;
        String isoTimestamp = parts[3];

        try {
            // 1) bid / ask parse
            bid = Double.parseDouble(parts[1]);
            ask = Double.parseDouble(parts[2]);

            // 2) ISO-8601 parse
            OffsetDateTime odt = OffsetDateTime.parse(isoTimestamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME);

            // 3) DB kaydı
            TblRates entity = new TblRates();
            entity.setRateName(rateName);
            entity.setBid(bid);
            entity.setAsk(ask);
            entity.setRateUpdateTime(odt.toLocalDateTime());
            entity.setDbUpdateTime(LocalDateTime.now());

            ratesRepository.save(entity);
            System.out.println("✅ Saved to DB (ISO): " + entity);

        } catch (NumberFormatException e) {
            // bid/ask parse hatası
            System.err.println("❌ Bid/Ask parse error for message: " + message + " => " + e.getMessage());
        } catch (Exception e) {
            // timestamp parse hatası, DB hatası vb.
            System.err.println("❌ Error processing message: " + message + " => " + e.getMessage());
        }
    }
}
