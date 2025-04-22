package com.mydomain.consumer.rates_consumer.service;

import com.mydomain.consumer.rates_consumer.model.TblRates;
import com.mydomain.consumer.rates_consumer.repository.RatesRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * KafkaConsumerService sınıfı, belirtilen Kafka topicinden gelen
 * döviz kuru mesajlarını dinler ve işleyerek veritabanına kaydeder.
 */
@Service
public class KafkaConsumerService {

    private static final Logger logger = LogManager.getLogger(KafkaConsumerService.class);

    @Autowired
    private RatesRepository ratesRepository;

    /**
     * KafkaListener metodu. Gelen mesajı okur, doğru formatta olup olmadığını kontrol eder,
     * parçalar (rateName, bid, ask, timestamp) ve TblRates entity'sine dönüştürerek
     * veritabanına kaydeder.
     *
     * @param record Kafka'dan gelen mesaj kaydı (partition, offset ve değer içerir)
     */
    @KafkaListener(topics = "#{T(com.mydomain.consumer.rates_consumer.config.ConfigReader).getTopicName()}")
    public void consumeRateMessage(ConsumerRecord<String, String> record) {
        String message = record.value();
        logger.info("Received message from Kafka partition={} offset={} => {}",
                record.partition(), record.offset(), message);

        String[] parts = message.split("\\|");
        if (parts.length < 4) {
            logger.error("Invalid message format, skipping => {}", message);
            return;
        }

        String rateName = parts[0];
        double bid;
        double ask;
        String isoTimestamp = parts[3];

        try {
            bid = Double.parseDouble(parts[1]);
            ask = Double.parseDouble(parts[2]);

            // "2025-03-09T12:58:21.000Z" format
            OffsetDateTime odt = OffsetDateTime.parse(isoTimestamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME);

            TblRates entity = new TblRates();
            entity.setRateName(rateName);
            entity.setBid(bid);
            entity.setAsk(ask);
            entity.setRateUpdateTime(odt.toLocalDateTime()); // store as LocalDateTime
            entity.setDbUpdateTime(LocalDateTime.now());

            ratesRepository.save(entity);
            logger.info("✅ Saved to DB => rateName={}, bid={}, ask={}, rateUpdateTime={}",
                    rateName, bid, ask, odt.toLocalDateTime());

        } catch (Exception e) {
            logger.error("Error processing message => {}, error={}", message, e.getMessage(), e);
        }
    }

}
