package com.mydomain.main.kafka;

import com.mydomain.main.exception.KafkaException;
import com.mydomain.main.model.Rate;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.*;

public class KafkaProducerService {

    private static final Logger logger = LogManager.getLogger(KafkaProducerService.class);

    private volatile KafkaProducer<String, String> producer;

    private final String bootstrapServers;
    private final String topicName;
    private final String acks;
    private final int retries;
    private final int deliveryTimeoutMs;
    private final int requestTimeoutMs;
    private final long reinitPeriodSec;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "kafka-producer-reinit");
                t.setDaemon(true);
                return t;
            });

    public KafkaProducerService(String bootstrapServers,
                                String topicName,
                                String acks,
                                int retries,
                                int deliveryTimeoutMs,
                                int requestTimeoutMs,
                                long reinitPeriodSec) {
        this.bootstrapServers = bootstrapServers;
        this.topicName = topicName;
        this.acks = acks;
        this.retries = retries;
        this.deliveryTimeoutMs = deliveryTimeoutMs;
        this.requestTimeoutMs = requestTimeoutMs;
        this.reinitPeriodSec = reinitPeriodSec;

        initProducer();
        scheduler.scheduleAtFixedRate(this::recoverProducerIfClosed, reinitPeriodSec, reinitPeriodSec, TimeUnit.SECONDS);
    }

    private synchronized void initProducer() {
        try {
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.ACKS_CONFIG, acks);
            props.put(ProducerConfig.RETRIES_CONFIG, retries);
            props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, deliveryTimeoutMs);
            props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, requestTimeoutMs);

            producer = new KafkaProducer<>(props);

            logger.info("✅ KafkaProducer READY  → bootstrap={}, topic={}, acks={}, retries={}, deliveryTimeoutMs={}, requestTimeoutMs={}",
                    bootstrapServers, topicName, acks, retries, deliveryTimeoutMs, requestTimeoutMs);
        } catch (Exception e) {
            producer = null;
            logger.warn("⚠️ KafkaProducer INIT FAILED: {}", e.getMessage());
        }
    }

    private void recoverProducerIfClosed() {
        if (producer != null) return;
        logger.info("♻️ Re-initializing Kafka producer...");
        initProducer();
    }

    public void sendRatesToKafka(Map<String, Rate> rates) {
        if (rates == null || rates.isEmpty()) {
            logger.debug("⏳ Skipping Kafka send: rate map is empty or null.");
            return;
        }

        rates.forEach(this::sendRateToKafka);
    }

    private void sendRateToKafka(String rateName, Rate rate) {
        if (producer == null) {
            throw new KafkaException("Kafka producer is null", rateName, null);
        }

        String timestamp = OffsetDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String payload = String.format("%s|%f|%f|%s",
                rateName,
                rate.getFields().getBid(),
                rate.getFields().getAsk(),
                timestamp);

        ProducerRecord<String, String> record = new ProducerRecord<>(topicName, payload);

        try {
            producer.send(record).get(requestTimeoutMs, TimeUnit.MILLISECONDS);
            logger.info("✅ Kafka OK (topic: {}) → {}", topicName, payload);
        } catch (Exception e) {
            closeProducerSilently();
            throw new KafkaException("Kafka send failed", payload, e);
        }
    }

    private void closeProducerSilently() {
        try {
            if (producer != null) {
                producer.close();
            }
        } catch (Exception ignored) {
        }
        producer = null;
    }
}
