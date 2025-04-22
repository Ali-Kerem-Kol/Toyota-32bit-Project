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
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * KafkaProducerService hesaplanan kurları RedisService üzerinden alır
 * ve Kafka topicine asenkron olarak gönderir
 * Kafka producer başlatılamazsa arka planda belirli aralıklarla yeniden başlatma dener
 */
public class KafkaProducerService {

    private static final Logger logger = LogManager.getLogger(KafkaProducerService.class);

    private volatile KafkaProducer<String, String> producer;
    private final String topicName;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final String bootstrapServers;
    private final String acks;
    private final int retries;

    private final String[] calculatedRateKeys = {"USDTRY", "EURTRY", "GBPTRY"};

    /**
     * KafkaProducerService nesnesini oluşturur ve producer ı başlatmayı dener
     * @param bootstrapServers Kafka broker adresi (örneğin "localhost:9092")
     */
    public KafkaProducerService(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
        this.topicName = ConfigReader.getKafkaTopicName();
        this.acks = ConfigReader.getKafkaAcks();
        this.retries = ConfigReader.getKafkaRetries();
        // Attempt initial producer creation; if fails, producer remains null.
        initProducer();
        // Schedule periodic attempts to initialize the producer if it is null.
        scheduler.scheduleAtFixedRate(() -> {
            if (producer == null) {
                logger.info("Attempting to reinitialize Kafka producer...");
                initProducer();
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    private synchronized void initProducer() {
        try {
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.ACKS_CONFIG, acks);
            props.put(ProducerConfig.RETRIES_CONFIG, retries);
            producer = new KafkaProducer<>(props);
            logger.info("KafkaProducer successfully created => bootstrap={}, topic={}, acks={}, retries={}",
                    bootstrapServers, topicName, acks, retries);
        } catch (Exception e) {
            logger.error("Failed to create Kafka producer: {}", e.getMessage());
            //logger.error("Failed to create Kafka producer: {}", e.getMessage(), e);
            producer = null;
        }
    }

    /**
     * RedisService üzerinden hesaplanan tüm kurları alır
     * ve Kafka topicine asenkron olarak gönderir
     * @param redis RedisService örneği
     */
    public void sendCalculatedRatesToKafka(RedisService redis) {
        for (String rateKey : calculatedRateKeys) {
            Rate calcRate = redis.getCalculatedRate(rateKey);
            if (calcRate == null) {
                logger.warn("Calculated rate '{}' not found.", rateKey);
                continue;
            }
            if (calcRate.getStatus() == null || !calcRate.getStatus().isActive() || !calcRate.getStatus().isUpdated()) {
                logger.warn("Calculation result '{}' has invalid RateStatus. Kafka message not sent.", rateKey);
                continue;
            }
            sendRateToKafka(rateKey, calcRate);
        }
    }

    /**
     * Tek bir kur mesajını Kafka topicine gönderir
     * @param calcRateName Gönderilecek kur adı
     * @param calcRate Gönderilecek Rate nesnesi
     */
    private void sendRateToKafka(String calcRateName, Rate calcRate) {
        double bid = calcRate.getFields().getBid();
        double ask = calcRate.getFields().getAsk();
        String isoTimestamp = OffsetDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String message = calcRateName + "|" + bid + "|" + ask + "|" + isoTimestamp;

        if (producer == null) {
            logger.error("Kafka producer is not available. Skipping message: {}", message);
            return;
        }

        ProducerRecord<String, String> record = new ProducerRecord<>(topicName, message);
        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                logger.error("Failed to send message to Kafka: {}. Error: {}", message, exception.getMessage(), exception);
                // İsteğe bağlı: Burada yeniden deneme mekanizması eklenebilir.
            } else {
                logger.info("Asynchronously sent to Kafka (topic={}): {} (partition={}, offset={})",
                        topicName, message, metadata.partition(), metadata.offset());
            }
        });
    }
}
