package com.mydomain.main;

import com.mydomain.main.config.ConfigReader;
import com.mydomain.main.coordinator.Coordinator;
import com.mydomain.main.exception.ProviderInitializationException;
import com.mydomain.main.service.KafkaProducerService;
import com.mydomain.main.service.RateCalculatorService;
import com.mydomain.main.service.RedisConsumerService;
import com.mydomain.main.service.RedisProducerService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.JedisPool;


public class CoordinatorApplication {

    private static final Logger logger = LogManager.getLogger(CoordinatorApplication.class);


    public static void main(String[] args) {
        logger.info("=== Starting Coordinator... ===");

        // Redis bağlantılarını ve servisleri başlat
        JedisPool jedisPool = new JedisPool(ConfigReader.getRedisHost(), ConfigReader.getRedisPort());

        RedisProducerService redisRawProducer = new RedisProducerService(jedisPool,ConfigReader.getRawStreamName(),ConfigReader.getStreamMaxLen());
        RedisConsumerService redisRawConsumer = new RedisConsumerService(jedisPool,ConfigReader.getRawStreamName(),ConfigReader.getRawGroup(),ConfigReader.getRawConsumerName(),ConfigReader.getStreamReadCount(),ConfigReader.getStreamBlockMillis());

        RedisProducerService redisCalculatedProducer = new RedisProducerService(jedisPool,ConfigReader.getCalcStreamName(),ConfigReader.getStreamMaxLen());
        RedisConsumerService redisCalculatedConsumer = new RedisConsumerService(jedisPool,ConfigReader.getCalcStreamName(),ConfigReader.getCalcGroup(),ConfigReader.getCalcConsumerName(),ConfigReader.getStreamReadCount(),ConfigReader.getStreamBlockMillis());

        // Hesaplama servisini başlat
        RateCalculatorService rateCalculatorService = new RateCalculatorService();

        // Kafka üretici servisini başlat
        KafkaProducerService kafkaProducerService = new KafkaProducerService();

        // Coordinator'ı başlat
        // bu arada bunu ICoordinator olarak mı kullanmalıyım ?? ama o zamanda "loadProviders" ve "stratRateStreamPipeline" metodlarını kullanamam
        Coordinator coordinator = new Coordinator(redisRawProducer, redisRawConsumer, redisCalculatedProducer, redisCalculatedConsumer, rateCalculatorService, kafkaProducerService);

        try {
            coordinator.loadProviders(ConfigReader.getProviders());
        } catch (ProviderInitializationException pie) {
            logger.fatal("Provider init fatal: {}", pie.getMessage(), pie);
        }

        /* --- Hesaplama + Kafka döngüsünü başlat --- */
        coordinator.startRateStreamPipeline();

        logger.info("=== Coordinator up & running ===");

        /* Main thread’i canlı tut */
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            logger.error("Main thread interrupted => shutting down", e);
        }
    }

}
