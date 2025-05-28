package com.mydomain.main.coordinator;

import com.mydomain.main.exception.KafkaPublishingException;
import com.mydomain.main.exception.ProviderInitializationException;
import com.mydomain.main.model.Rate;
import com.mydomain.main.model.RateFields;
import com.mydomain.main.model.RateStatus;
import com.mydomain.main.provider.IProvider;
import com.mydomain.main.service.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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

    private final ConcurrentMap<String, Rate> lastRates = new ConcurrentHashMap<>(); // En son değer cache

    public Coordinator(RedisProducerService redisRawProducer,
                       RedisConsumerService redisRawConsumer,
                       RedisProducerService redisCalculatedProducer,
                       RedisConsumerService redisCalculatedConsumer,
                       RateCalculatorService rateCalculatorService,
                       KafkaProducerService kafkaProducerService) {
        this.redisRawProducer = redisRawProducer;
        this.redisRawConsumer = redisRawConsumer;
        this.redisCalculatedProducer = redisCalculatedProducer;
        this.redisCalculatedConsumer = redisCalculatedConsumer;
        this.rateCalculatorService = rateCalculatorService;
        this.kafkaProducerService = kafkaProducerService;
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
        if (lastRates.putIfAbsent(rateName, rate) != null) {
            logger.warn("Rate already registered: {}", rateName);
            return;
        }

        redisRawProducer.publishRate(rateName, rate);
        logger.info("📈 New Rate Available ({}): {}", platform, rate);
    }

    @Override
    public void onRateUpdate(String platform, String rateName, RateFields fields) {
        Rate updatedRate = lastRates.computeIfPresent(rateName, (k, existing) -> {
            existing.setFields(fields);
            return existing;
        });

        if (updatedRate == null) {
            logger.warn("Rate not found for update: {} (call onRateAvailable first)", rateName);
            return;
        }

        redisRawProducer.publishRate(rateName, updatedRate);
        logger.info("📊 Rate Updated ({}): {} -> {}", platform, rateName, fields);
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

            String className  = def.getString("className");
            String platformName = def.optString("platformName", className);

            pool.submit(() -> {
                try {

                    IProvider p = (IProvider) Class.forName(className)
                            .getDeclaredConstructor()
                            .newInstance();
                    p.setCoordinator(this);

                    /* 1️⃣ — rate listesi önce kuyruğa düşsün */
                    def.getJSONArray("subscribeRates").forEach(r -> p.subscribe(platformName, r.toString()));

                    /* 2️⃣ — provider kendi json’ını okuyarak bağlanır    */
                    p.connect(platformName, Map.of());         // parametre yok

                    logger.info("🔧 Provider up: {}", className);

                } catch (Exception e) {
                    throw new ProviderInitializationException("Cannot instantiate " + className, e);
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
                    redisCalculatedProducer.publishRates(calculatedRates);

                    // 4. calculated stream’den oku ve Kafka’ya gönder
                    Map<String, Rate> ratesToSend = redisCalculatedConsumer.readRatesAsMap();
                    kafkaProducerService.sendRatesToKafka(ratesToSend);
                } catch (KafkaPublishingException e) {
                    logger.error("❗ Kafka publish error: {}", e.getMessage(), e);
                }
                catch (Exception e) {
                    logger.error("❌ Error in stream consumer loop: {}", e.getMessage(), e);
                }
            }
        }, "stream-reader-thread");

        t.setDaemon(true);
        t.start();
    }





}
