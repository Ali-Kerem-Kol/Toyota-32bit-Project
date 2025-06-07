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
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.*;

public class KafkaProducerService {

    private static final Logger log = LogManager.getLogger(KafkaProducerService.class);

    private volatile KafkaProducer<String, String> producer;

    private final String bootstrapServers;
    private final String topicName;
    private final String acks;
    private final int retries;
    private final int deliveryTimeoutMs;
    private final int requestTimeoutMs;

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

            log.info("✅ KafkaProducer READY → bootstrap={}, topic={}, acks={}, retries={}, deliveryTimeoutMs={}, requestTimeoutMs={}",
                    bootstrapServers, topicName, acks, retries, deliveryTimeoutMs, requestTimeoutMs);
        } catch (Exception e) {
            producer = null;
            log.warn("⚠️ KafkaProducer INIT FAILED: {}", e.getMessage());
        }
    }

    private void recoverProducerIfClosed() {
        if (producer != null) return;
        log.info("♻️ Re-initializing Kafka producer...");
        initProducer();
    }

    public void sendRateToKafka(Rate rate) {
        if (producer == null) {
            throw new KafkaException("Kafka producer is null", rate.getRateName(), null);
        }

        String timestamp = OffsetDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        String payload = String.format("%s|%f|%f|%s",
                rate.getRateName(),
                rate.getFields().getBid(),
                rate.getFields().getAsk(),
                timestamp);

        ProducerRecord<String, String> record = new ProducerRecord<>(topicName, rate.getRateName(), payload);

        try {
            producer.send(record).get(requestTimeoutMs, TimeUnit.MILLISECONDS);
            log.info("✅ Kafka OK → {}", payload);
        } catch (Exception e) {
            closeProducerSilently();
            throw new KafkaException("Kafka send failed", payload, e);
        }
    }

    public List<Rate> sendRatesToKafka(List<Rate> rates) {
        List<Rate> successfullySent = new ArrayList<>();

        if (rates == null || rates.isEmpty()) {
            log.debug("⏳ Skipping Kafka send: rate list is empty.");
            return successfullySent;
        }

        for (Rate rate : rates) {
            if (producer == null) {
                log.error("❌ Kafka producer unavailable, skipping rate: {}", rate.getRateName());
                continue;
            }

            String timestamp = OffsetDateTime.now(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

            String payload = String.format("%s|%f|%f|%s",
                    rate.getRateName(),
                    rate.getFields().getBid(),
                    rate.getFields().getAsk(),
                    timestamp);

            ProducerRecord<String, String> record = new ProducerRecord<>(topicName, rate.getRateName(), payload);

            try {
                producer.send(record).get(requestTimeoutMs, TimeUnit.MILLISECONDS);
                log.info("✅ Kafka OK → {}", payload);
                successfullySent.add(rate);
            } catch (Exception e) {
                log.error("❌ Kafka send failed for rate: {} → {}", rate.getRateName(), e.getMessage());
                closeProducerSilently(); // force reinit
            }
        }

        return successfullySent;
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
