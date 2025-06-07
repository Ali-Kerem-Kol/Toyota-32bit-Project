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

