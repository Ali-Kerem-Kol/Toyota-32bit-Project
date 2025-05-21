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
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Hesaplanmış oranları Kafka'ya gönderir — self-healing producer.
 */
public final class KafkaProducerService {

    private static final Logger log = LogManager.getLogger(KafkaProducerService.class);

    private volatile KafkaProducer<String, String> producer;

    private final String topicName;
    private final String bootstrapServers;
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


    public KafkaProducerService(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
        this.topicName = ConfigReader.getKafkaTopicName();
        this.acks = ConfigReader.getKafkaAcks();
        this.retries = ConfigReader.getKafkaRetries();
        this.deliveryTimeoutMs = ConfigReader.getKafkaDeliveryTimeout();
        this.requestTimeoutMs = ConfigReader.getKafkaRequestTimeout();
        this.reinitPeriodSec = ConfigReader.getKafkaReinitPeriod();

        initProducer();
        scheduler.scheduleAtFixedRate(this::ensureProducer, reinitPeriodSec, reinitPeriodSec, TimeUnit.SECONDS);
    }

    private synchronized void initProducer() {
        try {
            Properties p = new Properties();
            p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            p.put(ProducerConfig.ACKS_CONFIG, acks);
            p.put(ProducerConfig.RETRIES_CONFIG, retries);
            p.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, deliveryTimeoutMs);
            p.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, requestTimeoutMs);

            producer = new KafkaProducer<>(p);
            log.info("KafkaProducer READY  → bootstrap={}, topic={}, acks={}, retries={}, " +
                            "deliveryTimeoutMs={}, requestTimeoutMs={}",
                    bootstrapServers, topicName, acks, retries, deliveryTimeoutMs, requestTimeoutMs);

        } catch (Exception e) {
            log.error("KafkaProducer INIT FAILED: {}", e.toString());
            producer = null;
        }
    }

    private void ensureProducer() {
        if (producer == null) {
            log.info("Re-initialising Kafka producer…");
            initProducer();
        }
    }


    /**
     * Yeni yöntem: Verilen oranları Kafka’ya yollar.
     */
    public void sendCalculatedRatesToKafka(Map<String, Rate> rates) {
        if (rates == null || rates.isEmpty()) {
            log.warn("Kafka gönderimi için boş veri seti alındı.");
            return;
        }

        rates.forEach(this::sendRateToKafka);
    }

    private void sendRateToKafka(String rateName, Rate rate) {
        if (producer == null) {
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

        ProducerRecord<String, String> rec = new ProducerRecord<>(topicName, payload);

        producer.send(rec, (meta, ex) -> {
            if (ex != null) {
                log.error("Kafka SEND FAIL [{}] → {}", payload, ex.toString());
                try { producer.close(); } catch (Exception ignore) {}
                producer = null;
            } else {
                log.info("Kafka OK  ({}-{} @ offset {}) → {}",
                        meta.topic(), meta.partition(), meta.offset(), payload);
            }
        });
    }
}
