package com.mydomain.main.service;

import com.mydomain.main.config.ConfigReader;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Hesaplanmış oranları Kafka'ya gönderir — self-healing producer.
 */
public final class KafkaProducerService {

    private static final Logger log = LogManager.getLogger(KafkaProducerService.class);

    private volatile KafkaProducer<String, String> KAFKA_PRODUCER;

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


    public KafkaProducerService() {
        this.bootstrapServers = ConfigReader.getKafkaBootstrapServers();
        this.topicName = ConfigReader.getKafkaTopicName();
        this.acks = ConfigReader.getKafkaAcks();
        this.retries = ConfigReader.getKafkaRetries();
        this.deliveryTimeoutMs = ConfigReader.getKafkaDeliveryTimeout();
        this.requestTimeoutMs = ConfigReader.getKafkaRequestTimeout();
        this.reinitPeriodSec = ConfigReader.getKafkaReinitPeriod();

        initProducer();
        scheduler.scheduleAtFixedRate(this::recoverProducerIfClosed, reinitPeriodSec, reinitPeriodSec, TimeUnit.SECONDS);
    }
    private synchronized void initProducer() {
        try {
            Properties properties = new Properties();

            properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            properties.put(ProducerConfig.ACKS_CONFIG, acks);
            properties.put(ProducerConfig.RETRIES_CONFIG, retries);
            properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, deliveryTimeoutMs);
            properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, requestTimeoutMs);

            KAFKA_PRODUCER = new KafkaProducer<>(properties);
            log.info("KafkaProducer READY  → bootstrap={}, topic={}, acks={}, retries={}, " +
                            "deliveryTimeoutMs={}, requestTimeoutMs={}",
                    bootstrapServers, topicName, acks, retries, deliveryTimeoutMs, requestTimeoutMs);

        } catch (Exception e) {
            log.error("KafkaProducer INIT FAILED: {}", e.toString());
            KAFKA_PRODUCER = null;
        }
    }

    private void recoverProducerIfClosed() {
        if (KAFKA_PRODUCER != null) return;

        log.info("Re-initialising Kafka producer…");
        initProducer();
    }


    /**
     * Yeni yöntem: Verilen oranları Kafka’ya yollar.
     */
    public void sendRatesToKafka(Map<String, Rate> rates) {
        if (rates == null || rates.isEmpty()) {
            log.warn("Kafka gönderimi için boş veri seti alındı.");
            return;
        }

        rates.forEach(this::sendRateToKafka);
    }

    private void sendRateToKafka(String rateName, Rate rate) {
        if (KAFKA_PRODUCER == null) {
            log.error("Kafka producer NULL  → mesaj atlandı: {}", rateName);
            return;
        }

        String tsIso = OffsetDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        String payload = String.format("%s|%f|%f|%s",
                rateName,
                rate.getFields().getBid(),
                rate.getFields().getAsk(),
                tsIso);

        ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topicName, payload);

        KAFKA_PRODUCER.send(producerRecord, (meta, ex) -> {
            if (ex != null) {
                log.error("Kafka SEND FAIL [{}] → {}", payload, ex.toString());
                try {
                    KAFKA_PRODUCER.close();
                } catch (Exception ignore) {

                }
                KAFKA_PRODUCER = null;
            } else {
                log.info("Kafka OK  ({}-{} @ offset {}) → {}",
                        meta.topic(), meta.partition(), meta.offset(), payload);
            }
        });
    }
}
