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
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Hesaplanmış oranları Kafka'ya gönderir — self-healing producer.
 *   • Producer init parametreleri config.json’dan gelir
 *   • Producer yoksa her reinitPeriodSec saniyede bir yeniden kurulur
 *   • send() -> callback ile başarı / hata loglanır
 *   • Hata varsa producer kapatılır; bir sonraki döngüde yeniden kurulur
 */
public final class KafkaProducerService {

    private static final Logger log = LogManager.getLogger(KafkaProducerService.class);

    /** Volatile → bütün thread’ler güncel referansı görür. */
    private volatile KafkaProducer<String, String> producer;

    // ------ config’ten gelen değerler ------
    private final String topicName;
    private final String bootstrapServers;
    private final String acks;
    private final int    retries;
    private final int    deliveryTimeoutMs;
    private final int    requestTimeoutMs;
    private final long   reinitPeriodSec;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "kafka-producer-reinit");
                t.setDaemon(true);
                return t;
            });

    private final Set<String> calculatedRateKeys;
    private final RedisService redisService;

    // ===========================================================================
    // ctor
    // ===========================================================================
    public KafkaProducerService(String bootstrapServers, RedisService redisService) {

        // ---- temel config ----
        this.bootstrapServers  = bootstrapServers;
        this.redisService      = redisService;
        this.topicName         = ConfigReader.getKafkaTopicName();
        this.acks              = ConfigReader.getKafkaAcks();

        // ---- gelişmiş ayarlar ----
        this.retries           = ConfigReader.getKafkaRetries();            // int
        this.deliveryTimeoutMs = ConfigReader.getKafkaDeliveryTimeout();    // int
        this.requestTimeoutMs  = ConfigReader.getKafkaRequestTimeout();     // int
        this.reinitPeriodSec   = ConfigReader.getKafkaReinitPeriod();       // long

        // USDTRY, EURTRY, … gibi result anahtarlarını hazırla
        calculatedRateKeys = ConfigReader.getSubscribeRatesShort()
                .stream()
                .map(sn -> sn.endsWith("USD") && !sn.equals("USDTRY")
                        ? sn.substring(0, 3) + "TRY"
                        : sn)
                .collect(Collectors.toSet());

        initProducer(); // ilk deneme

        // arka planda “producer == null” mı diye bakar ve gerekiyorsa yeniden kurar
        scheduler.scheduleAtFixedRate(this::ensureProducer,
                reinitPeriodSec, reinitPeriodSec, TimeUnit.SECONDS);
    }

    // ===========================================================================
    // Producer lifecycle
    // ===========================================================================
    private synchronized void initProducer() {
        try {
            Properties p = new Properties();
            p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG   , bootstrapServers);
            p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                    StringSerializer.class.getName());
            p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                    StringSerializer.class.getName());

            // güvenilirlik + timeout
            p.put(ProducerConfig.ACKS_CONFIG               , acks);
            p.put(ProducerConfig.RETRIES_CONFIG            , retries);
            p.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, deliveryTimeoutMs);
            p.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG , requestTimeoutMs);

            producer = new KafkaProducer<>(p);
            log.info("KafkaProducer READY  → bootstrap={}, topic={}, acks={}, retries={}, " +
                            "deliveryTimeoutMs={}, requestTimeoutMs={}",
                    bootstrapServers, topicName, acks, retries,
                    deliveryTimeoutMs, requestTimeoutMs);

        } catch (Exception e) {
            log.error("KafkaProducer INIT FAILED: {}", e.toString());
            producer = null;          // bir sonraki döngüde yeniden denenir
        }
    }

    /** scheduler tarafından çağrılır — producer yoksa yeniden kurar */
    private void ensureProducer() {
        if (producer == null) {
            log.info("Re-initialising Kafka producer…");
            initProducer();
        }
    }

    // ===========================================================================
    //  Public API
    // ===========================================================================
    /** Redis’ten tüm hesaplanmış oranları alır ve Kafka’ya yollar. */
    public void sendCalculatedRatesToKafka() {
        calculatedRateKeys.forEach(key -> {
            Rate r = redisService.getCalculatedRate(key);
            if (r != null) {
                sendRateToKafka(key, r);
            }
        });
    }

    // ===========================================================================
    //  Internal helpers
    // ===========================================================================
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

        // Her gönderimde callback ile logla ve hata varsa producer'ı resetle
        producer.send(rec, (meta, ex) -> {
            if (ex != null) {
                log.error("Kafka SEND FAIL [{}] → {}", payload, ex.toString());

                // ======= kendini onarma: producer kapat & null yap =========
                try { producer.close(); } catch (Exception ignore) {}
                producer = null;  // -> ensureProducer() yeni producer yaratacak
            } else {
                log.info("Kafka OK  ({}-{} @ offset {}) → {}",
                        meta.topic(), meta.partition(), meta.offset(), payload);
            }
        });
    }
}
