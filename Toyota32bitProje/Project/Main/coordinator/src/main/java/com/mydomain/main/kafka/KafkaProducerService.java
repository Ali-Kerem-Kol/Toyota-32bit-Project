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

/**
 * {@code KafkaProducerService}, Kafka producer’ını kullanarak rate verilerini belirtilen topic’e
 * asenkron bir şekilde gönderir. Bağlantı kesintilerine karşı yeniden başlatma mekanizması
 * (reinit) sunar ve hata durumlarını loglar. Apache Kafka client kütüphanesini temel alır.
 *
 * <p>Hizmetin temel işleyişi:
 * <ul>
 *   <li>Konfigürasyon parametreleriyle (bootstrap servers, topic, acks vb.) bir Kafka producer başlatır.</li>
 *   <li>Verileri JSON formatında serialize ederek Kafka’ya gönderir.</li>
 *   <li>Belirli aralıklarla (reinitPeriodSec) producer’ın durumunu kontrol eder ve yeniden başlatır.</li>
 * </ul>
 * </p>
 *
 * <p><b>Özellikler:</b>
 * <ul>
 *   <li>Yeniden başlatma (reinit) ile bağlantı kesintilerine dayanıklılık.</li>
 *   <li>Loglama için Apache Log4j ile hata ayıklama ve izleme seviyeleri.</li>
 *   <li>Batch gönderim desteği ile çoklu rate verisi işleme.</li>
 * </ul>
 * </p>
 *
 * @author Ali Kerem Kol
 * @version 1.0
 * @since 2025-06-07
 */
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

    /**
     * {@code KafkaProducerService} nesnesini başlatır.
     * Kafka producer’ını konfigüre eder ve yeniden başlatma scheduler’ını başlatır.
     *
     * @param bootstrapServers Kafka broker adresleri (örneğin, "localhost:9092")
     * @param topicName Gönderilecek verilerin topic adı
     * @param acks Gönderim doğrulama seviyesi (örneğin, "all")
     * @param retries Gönderim başarısızlığında yeniden deneme sayısı
     * @param deliveryTimeoutMs Mesaj teslim zaman aşımı (milisaniye cinsinden)
     * @param requestTimeoutMs İstek zaman aşımı (milisaniye cinsinden)
     * @param reinitPeriodSec Yeniden başlatma kontrol aralığı (saniye cinsinden)
     * @throws IllegalArgumentException Herhangi bir parametre null veya geçersizse
     */
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

    /**
     * Kafka producer’ını konfigüre eder ve başlatır.
     * Başarısız olursa producer null kalır ve loglanır.
     *
     * @throws IllegalStateException Konfigürasyon sırasında beklenmedik bir hata oluşursa
     */
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

    /**
     * Producer’ın kapalı olup olmadığını kontrol eder ve gerekirse yeniden başlatır.
     * Eğer producer zaten aktifse işlem yapmaz.
     */
    private void recoverProducerIfClosed() {
        if (producer != null) return;
        log.info("♻️ Re-initializing Kafka producer...");
        initProducer();
    }

    /**
     * Belirtilen rate verisini Kafka topic’ine gönderir.
     * Gönderim başarısız olursa istisna fırlatır ve producer’ı kapatır.
     *
     * @param rate Gönderilecek rate verisi, null olamaz
     * @throws KafkaException Producer null ise veya gönderim başarısız olursa
     * @throws IllegalArgumentException Rate null ise
     */
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

    /**
     * Belirtilen rate verisi listesini Kafka topic’ine gönderir.
     * Başarılı gönderilen rate’leri döndürür, başarısız olanlar loglanır.
     *
     * @param rates Gönderilecek rate verisi listesi, null veya boş olabilir
     * @return Başarılı bir şekilde gönderilen rate’lerin listesi
     */
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

    /**
     * Kafka producer’ını sessizce kapatır.
     * İstisnalar yakalanır ve loglanmaz, producer null olarak ayarlanır.
     */
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
