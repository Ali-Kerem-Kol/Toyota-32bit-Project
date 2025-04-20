package com.mydomain.main.coordinator;

import com.mydomain.main.service.RateCalculatorService;
import com.mydomain.main.service.RedisService;
import com.mydomain.main.model.Rate;
import com.mydomain.main.model.RateFields;
import com.mydomain.main.model.RateStatus;
import com.mydomain.main.service.KafkaProducerService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Coordinator is the main class of the data collection application.
 * It implements the CoordinatorInterface (callbacks),
 * holds a distributed cache of Rate objects using Redis, handles calculations,
 * and sends results to Kafka.
 */
public class Coordinator implements ICoordinator {

    private static final Logger logger = LogManager.getLogger(Coordinator.class);

    private final RedisService redisService;
    private final RateCalculatorService rateCalculatorService;
    private final KafkaProducerService kafkaProducerService;

    public Coordinator(RedisService redisService, RateCalculatorService rateCalculatorService, KafkaProducerService kafkaProducerService) {
        this.redisService = redisService;
        this.rateCalculatorService = rateCalculatorService;
        this.kafkaProducerService = kafkaProducerService;
    }


    @Override
    public void onConnect(String platformName, Boolean status) {
        logger.info("🔗 {} connection status: {}", platformName, status ? "Connected" : "Disconnected");
    }

    @Override
    public void onDisConnect(String platformName, Boolean status) {
        logger.info("🔴 {} disconnected.", platformName);
    }

    @Override
    public void onRateAvailable(String platformName, String rateName, Rate rate) {
        redisService.putRawRate(rateName, rate);
        logger.info("📈 New Rate Available ({}): {}", platformName, rate);
        rateCalculatorService.calculateRates(redisService);
    }

    @Override
    public void onRateUpdate(String platformName, String rateName, RateFields rateFields) {
        try {
            Rate rate = redisService.getRawRate(rateName);
            if (rate != null) {
                rate.setFields(rateFields);
                redisService.putRawRate(rateName, rate);
            }
            logger.info("📊 Rate Updated ({}): {} -> {}", platformName, rateName, rateFields);
            rateCalculatorService.calculateRates(redisService);
            kafkaProducerService.sendCalculatedRatesToKafka(redisService);
        } catch (Exception e) {
            logger.error("❌ Error in onRateUpdate: {}", e.getMessage(), e);
        }
    }

    @Override
    public void onRateStatus(String platformName, String rateName, RateStatus rateStatus) {
        Rate rate = redisService.getRawRate(rateName);
        if (rate != null) {
            rate.setStatus(rateStatus);
            redisService.putRawRate(rateName, rate);
        }
        logger.info("ℹ️ Rate Status Updated ({}): {} -> {}", platformName, rateName, rateStatus);
    }

    @Override
    public Rate fetchRateFromRest(String platformName, String rateName) {
        logger.warn("fetchRateFromRest => not implemented in this minimal example. Use direct REST calls if needed.");
        return null;
    }


}
