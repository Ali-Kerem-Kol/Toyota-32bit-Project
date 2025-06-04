package com.mydomain.main.coordinator;

import com.mydomain.main.cache.RateCache;
import com.mydomain.main.calculation.RateCalculatorService;
import com.mydomain.main.config.ConfigReader;
import com.mydomain.main.exception.FormulaEngineException;
import com.mydomain.main.exception.KafkaException;
import com.mydomain.main.exception.RedisException;
import com.mydomain.main.kafka.KafkaProducerService;
import com.mydomain.main.model.Rate;
import com.mydomain.main.model.RateFields;
import com.mydomain.main.model.RateStatus;
import com.mydomain.main.provider.IProvider;
import com.mydomain.main.redis.RedisConsumerService;
import com.mydomain.main.redis.RedisProducerService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Coordinator implements ICoordinator {

    private static final Logger logger = LogManager.getLogger(Coordinator.class);

    private final RedisProducerService redisRawProducer;
    private final RedisConsumerService redisRawConsumer;
    private final RedisProducerService redisCalculatedProducer;
    private final RedisConsumerService redisCalculatedConsumer;

    private final RateCalculatorService rateCalculatorService;
    private final KafkaProducerService kafkaProducerService;

    private final RateCache cache;

    public Coordinator(RedisProducerService redisRawProducer,
                       RedisConsumerService redisRawConsumer,
                       RedisProducerService redisCalculatedProducer,
                       RedisConsumerService redisCalculatedConsumer,
                       RateCalculatorService rateCalculatorService,
                       KafkaProducerService kafkaProducerService,
                       RateCache cache) {
        this.redisRawProducer = redisRawProducer;
        this.redisRawConsumer = redisRawConsumer;
        this.redisCalculatedProducer = redisCalculatedProducer;
        this.redisCalculatedConsumer = redisCalculatedConsumer;
        this.rateCalculatorService = rateCalculatorService;
        this.kafkaProducerService = kafkaProducerService;
        this.cache = cache;
    }

    @Override
    public void onConnect(String platformName, Boolean status) {
        logger.info("🔗 {} connection status: {}", platformName, status ? "Connected" : "Disconnected");
    }

    @Override
    public void onDisConnect(String platformName, Boolean status) {
        logger.info("🔗 {} connection status: {}", platformName, status ? "Connected" : "Disconnected");
    }

    @Override
    public void onRateAvailable(String platform, String rateName, Rate rate) {
        try {
            redisRawProducer.publishSingleRate(rate);
            logger.info("📈 New Rate Available ({}): {}", platform, rate);
        } catch (RedisException e) {
            logger.error("❗ Redis error: {}", e.getMessage(), e);
        }
    }

    @Override
    public void onRateUpdate(String platform, String rateName, RateFields fields) {
        try {
            List<Rate> rates = cache.getActiveRates(platform, rateName);
            logger.debug("🧪 Active rates for {}/{}: {}", platform, rateName, rates.size());//1
            for (Rate rate : rates) {
                logger.debug("🧪 Sending rate to Redis: {}", rate);//1
                if (redisRawProducer.publishSingleRate(rate)) {
                    cache.markRateToNonActive(platform, rateName, rate);
                }
            }
            logger.info("📊 Rate Updated ({}): {} -> {}", platform, rateName, fields);
        } catch (RedisException e) {
            logger.error("❗ Redis error: {}", e.getMessage(), e);
        }
    }


    @Override
    public void onRateStatus(String platformName, String rateName, RateStatus rateStatus) {
        logger.info("ℹ️ Rate Status Updated ({}): {} -> {}", platformName, rateName, rateStatus);
    }


    public void loadProviders(JSONArray defs) {
        ExecutorService pool = Executors.newCachedThreadPool();
        Runtime.getRuntime().addShutdownHook(new Thread(pool::shutdownNow));

        for (int i = 0; i < defs.length(); i++) {
            final JSONObject def = defs.getJSONObject(i);
            String className = def.getString("className");
            String platformName = def.optString("platformName", className);
            JSONArray subscribeRates = def.optJSONArray("subscribeRates");

            pool.submit(() -> {
                try {
                    logger.info("🔄 Loading provider → class: {}, platform: {}", className, platformName);

                    IProvider provider = (IProvider) Class.forName(className)
                            .getDeclaredConstructor()
                            .newInstance();

                    provider.setCoordinator(this);
                    provider.setCache(this.cache);

                    if (subscribeRates != null) {
                        for (int j = 0; j < subscribeRates.length(); j++) {
                            String rate = subscribeRates.getString(j);
                            provider.subscribe(platformName, rate);
                        }
                    }

                    provider.connect(platformName, Map.of());

                    logger.info("✅ Provider started → {}", className);

                } catch (Exception e) {
                    logger.error("❌ Cannot instantiate or initialize provider: {}", className, e);
                }
            });
        }
    }


    public void startRateStreamPipeline() {
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // 1. raw_rates stream'den verileri oku ve grupla
                    Map<String, List<Rate>> groupedRawRates = redisRawConsumer.readAndGroupRatesByShortName();

                    // 2. Hesapla
                    Map<String, Rate> calculatedRates = rateCalculatorService.calculate(groupedRawRates);

                    // 3. calculated stream’e yaz
                    redisCalculatedProducer.publishMultipleRates(calculatedRates.values());

                    // 4. calculated stream’den oku ve Kafka’ya gönder
                    Map<String, Rate> ratesToSend = redisCalculatedConsumer.readRatesAsMap();
                    kafkaProducerService.sendRatesToKafka(ratesToSend);
                } catch (RedisException e) {
                    logger.error("❗ Redis error: {}", e.getMessage(), e);
                } catch (FormulaEngineException e) {
                    logger.error("❗ Formula engine error: {}", e.getMessage(), e);
                } catch (KafkaException e) {
                    logger.error("❗ Kafka error: {}", e.getMessage(), e);
                } catch (Exception e) {
                    logger.error("❌ Error in stream consumer loop: {}", e.getMessage(), e);
                }
            }
        }, "stream-reader-thread");

        t.setDaemon(true);
        t.start();
    }





}
