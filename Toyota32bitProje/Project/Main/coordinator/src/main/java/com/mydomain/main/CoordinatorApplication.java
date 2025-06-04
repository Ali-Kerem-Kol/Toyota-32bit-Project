package com.mydomain.main;

import com.mydomain.main.cache.RateCache;
import com.mydomain.main.config.ConfigReader;
import com.mydomain.main.coordinator.Coordinator;
import com.mydomain.main.exception.ConfigLoadException;
import com.mydomain.main.filter.*;
import com.mydomain.main.kafka.KafkaProducerService;
import com.mydomain.main.calculation.RateCalculatorService;
import com.mydomain.main.redis.RedisConsumerService;
import com.mydomain.main.redis.RedisProducerService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.JedisPool;


public class CoordinatorApplication {

    private static final Logger logger = LogManager.getLogger(CoordinatorApplication.class);

    public static void main(String[] args) {
        JedisPool jedisPool = null;
        try {
            logger.info("=== Starting Coordinator... ===");

            // Config dosyalarını oku ve doğrula
            ConfigReader.initConfigs();

            // Redis bağlantılarını ve servisleri başlat
            jedisPool = new JedisPool(ConfigReader.getRedisHost(), ConfigReader.getRedisPort());

            RedisProducerService redisRawProducer = new RedisProducerService(jedisPool,ConfigReader.getRawStreamName(),ConfigReader.getStreamMaxLen(),ConfigReader.getStreamRetention());
            RedisConsumerService redisRawConsumer = new RedisConsumerService(jedisPool,ConfigReader.getRawStreamName(),ConfigReader.getRawGroup(),ConfigReader.getRawConsumerName(),ConfigReader.getStreamReadCount(),ConfigReader.getStreamBlockMillis());

            RedisProducerService redisCalculatedProducer = new RedisProducerService(jedisPool,ConfigReader.getCalcStreamName(),ConfigReader.getStreamMaxLen(),ConfigReader.getStreamRetention());
            RedisConsumerService redisCalculatedConsumer = new RedisConsumerService(jedisPool,ConfigReader.getCalcStreamName(),ConfigReader.getCalcGroup(),ConfigReader.getCalcConsumerName(),ConfigReader.getStreamReadCount(),ConfigReader.getStreamBlockMillis());

            // Hesaplama servisini başlat
            RateCalculatorService rateCalculatorService = new RateCalculatorService(ConfigReader.getSubscribeRatesShort());

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

            // RateCache'i başlat
            RateCache rateCache = new RateCache(ConfigReader.getCacheSize(),filterService);

            // Coordinator'ı başlat
            Coordinator coordinator = new Coordinator(
                    redisRawProducer,
                    redisRawConsumer,
                    redisCalculatedProducer,
                    redisCalculatedConsumer,
                    rateCalculatorService,
                    kafkaProducerService,
                    rateCache
            );


            // Provider'ları yükle
            coordinator.loadProviders(ConfigReader.getProviders());

            // Hesaplama ve veri akışını başlat
            coordinator.startRateStreamPipeline();

            logger.info("=== Coordinator up & running ===");

            // Main thread'i canlı tut
            Thread.currentThread().join();

        } catch (ConfigLoadException e) {
            logger.fatal("🛑 Configuration failed to load: {}", e.getMessage());
        } catch (InterruptedException e) {
            logger.fatal("🛑 Main thread interrupted => shutting down", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.fatal("🛑 Unexpected error occurred", e);
        } finally {
            logger.info("=== Coordinator shutting down... ===");
            if (jedisPool != null) {
                jedisPool.close();
                logger.info("✅ Redis pool closed.");
            }
        }
    }
}

