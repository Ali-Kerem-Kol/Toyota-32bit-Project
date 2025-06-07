package com.mydomain.main;

import com.mydomain.main.calculation.DynamicFormulaService;
import com.mydomain.main.config.ConfigReader;
import com.mydomain.main.coordinator.Coordinator;
import com.mydomain.main.exception.ConfigLoadException;
import com.mydomain.main.filter.*;
import com.mydomain.main.kafka.KafkaProducerService;
import com.mydomain.main.calculation.RateCalculatorService;
import com.mydomain.main.redis.RedisService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.JedisPool;

/**
 * {@code CoordinatorApplication}, uygulamanın ana giriş noktasıdır ve koordinasyon
 * servislerini başlatır. `ConfigReader` ile yapılandırma dosyası yüklenir, Redis,
 * Kafka, hesaplama ve filtre servisleri initialize edilir, ardından `Coordinator`
 * ile veri akışı koordine edilir. Bu sınıf, uygulamanın yaşam döngüsünü yönetir.
 *
 * <p>Hizmetin temel işleyişi:
 * <ul>
 *   <li>Yapılandırma dosyası (`config.json`) okunur ve doğrulama yapılır.</li>
 *   <li>Redis, Kafka, hesaplama ve filtre servisleri başlatılır.</li>
 *   <li>`Coordinator` ile sağlayıcılar yüklenir ve hesaplama worker’ı çalıştırılır.</li>
 *   <li>Uygulama kapanışında kaynaklar (örneğin, JedisPool) güvenli bir şekilde serbest bırakılır.</li>
 * </ul>
 * </p>
 *
 * <p><b>Notlar:</b>
 * <ul>
 *   <li>Bu sınıf, ana thread’i canlı tutarak uygulamanın sürekli çalışmasını sağlar.</li>
 *   <li>Hata durumlarında loglama ile hata ayıklama yapılabilir.</li>
 * </ul>
 * </p>
 *
 * @author Ali Kerem Kol
 * @version 1.0
 * @since 2025-06-07
 */
public class CoordinatorApplication {

    private static final Logger log = LogManager.getLogger(CoordinatorApplication.class);

    public static void main(String[] args) {
        JedisPool jedisPool = null;
        try {
            log.info("=== Starting Coordinator... ===");

            // Config dosyalarını oku ve doğrula
            ConfigReader.initConfigs();

            // Redis bağlantılarını ve servisleri başlat
            jedisPool = new JedisPool(ConfigReader.getRedisHost(), ConfigReader.getRedisPort());

            // Hesaplama servisini başlat
            RateCalculatorService rateCalculatorService = new RateCalculatorService(ConfigReader.getSubscribeRates());

            // Kafka üretici servisini başlat
            KafkaProducerService kafkaProducerService = new KafkaProducerService(
                    ConfigReader.getKafkaBootstrapServers(),
                    ConfigReader.getKafkaTopicName(),
                    ConfigReader.getKafkaAcks(),
                    ConfigReader.getKafkaRetries(),
                    ConfigReader.getKafkaDeliveryTimeout(),
                    ConfigReader.getKafkaRequestTimeout(),
                    ConfigReader.getKafkaReinitPeriod()
            );

            // Filtre Servisini başlat
            FilterService filterService = new FilterService(ConfigReader.getFiltersObject());

            // Redis servisini başlat
            RedisService redisService = new RedisService(jedisPool, filterService,ConfigReader.getRedisTTLSeconds(), ConfigReader.getRedisMaxListSize());

            // Coordinator'ı başlat
            Coordinator coordinator = new Coordinator(redisService, rateCalculatorService, kafkaProducerService);

            // Provider'ları yükle
            coordinator.loadProviders(ConfigReader.getProviders());

            // Akışı başlat
            coordinator.startCalculationWorker(100);

            log.info("=== Coordinator up & running ===");

            // Main thread'i canlı tut
            Thread.currentThread().join();

        } catch (ConfigLoadException e) {
            log.fatal("🛑 Configuration failed to load: {}", e.getMessage());
        } catch (InterruptedException e) {
            log.fatal("🛑 Main thread interrupted => shutting down", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.fatal("🛑 Unexpected error occurred", e);
        } finally {
            log.info("=== Coordinator shutting down... ===");
            if (jedisPool != null) {
                jedisPool.close();
                log.info("✅ Redis pool closed.");
            }
        }
    }
}

