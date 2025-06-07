package com.mydomain.main.coordinator;

import com.mydomain.main.calculation.RateCalculatorService;
import com.mydomain.main.exception.CalculationException;
import com.mydomain.main.exception.KafkaException;
import com.mydomain.main.exception.RedisException;
import com.mydomain.main.kafka.KafkaProducerService;
import com.mydomain.main.model.Rate;
import com.mydomain.main.model.RateFields;
import com.mydomain.main.model.RateStatus;
import com.mydomain.main.provider.IProvider;
import com.mydomain.main.redis.RedisService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Coordinator implements ICoordinator {

    private static final Logger log = LogManager.getLogger(Coordinator.class);

    private final RedisService redisService;
    private final RateCalculatorService rateCalculatorService;
    private final KafkaProducerService kafkaProducerService;

    private ScheduledExecutorService scheduler;

    public Coordinator(RedisService redisService,
                       RateCalculatorService rateCalculatorService,
                       KafkaProducerService kafkaProducerService) {
        this.redisService = redisService;
        this.rateCalculatorService = rateCalculatorService;
        this.kafkaProducerService = kafkaProducerService;
    }

    @Override
    public void onConnect(String platformName, Boolean status) {
        log.info("🔗 {} connection status: {}", platformName, status ? "Connected" : "Disconnected");
    }

    @Override
    public void onDisConnect(String platformName, Boolean status) {
        log.info("🔗 {} connection status: {}", platformName, status ? "Connected" : "Disconnected");
    }

    @Override
    public void onRateAvailable(String platform, String rateName, Rate rate) {
        log.info("📈 New Rate Available ({}): {}", platform, rate);
    }

    @Override
    public void onRateUpdate(String platformName, String rateName, RateFields fields) {
        log.info("🔄 Rate Updated ({}): {} -> {}", platformName, rateName, fields);
    }

    @Override
    public void onRateStatus(String platformName, String rateName, RateStatus rateStatus) {
        log.info("ℹ️ Rate Status Updated ({}): {} -> {}", platformName, rateName, rateStatus);
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
                    log.info("🔄 Loading provider → class: {}, platform: {}", className, platformName);

                    IProvider provider = (IProvider) Class.forName(className)
                            .getDeclaredConstructor()
                            .newInstance();

                    provider.setCoordinator(this);
                    provider.setRedis(redisService);

                    if (subscribeRates != null) {
                        for (int j = 0; j < subscribeRates.length(); j++) {
                            String rate = subscribeRates.getString(j);
                            provider.subscribe(platformName, rate);
                        }
                    }

                    provider.connect(platformName, Map.of());

                    log.info("✅ Provider started → {}", className);

                } catch (Exception e) {
                    log.error("❌ Cannot instantiate or initialize provider: {}", className, e);
                }
            });
        }
    }



    /**
     * Sürekli olarak hesaplama ve publish işlemlerini yürüten worker thread başlatır.
     * Her 100 ms'de bir çalışır.
     */
    public void startCalculationWorker(long intervalMs) {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-calc-worker");
            t.setDaemon(true); // Uygulama kapanırken thread'ı otomatik sonlandırır
            return t;
        });

        scheduler.scheduleAtFixedRate(() -> {
            try {
                // 1. Aktif verileri topla
                Map<String, Map<String, Rate>> activeRawRates = redisService.getMostRecentAndActiveRawRates();

                // 2. Hesapla
                List<Rate> result = rateCalculatorService.calculate(activeRawRates);

                // 3. Kullanılan raw'ları pasifleştir
                if (!result.isEmpty()) redisService.deactivateRawRates(activeRawRates);

                // 4. Hesaplananları Redis'e yaz
                for (Rate calculatedRate : result)
                    redisService.putCalculatedRate(calculatedRate.getRateName(), calculatedRate);

                // 5. Son hesaplananları Kafka'ya gönder
                List<Rate> lastCalculatedRates = redisService.getMostRecentAndActiveCalculatedRates();
                List<Rate> successfullySent = kafkaProducerService.sendRatesToKafka(lastCalculatedRates);

                // 6. Kafka'ya gönderilenleri pasifleştir
                redisService.deactivateCalculatedRates(successfullySent);

            } catch (RedisException e) {
                log.error("❌ Redis hatası (worker): {}", e.getMessage());
            } catch (CalculationException e) {
                log.error("❌ Hesaplama hatası (worker): {}", e.getMessage());
            } catch (KafkaException e) {
                log.error("❌ Kafka hatası (worker): {}", e.getMessage());
            } catch (Exception e) {
                log.error("❌ Beklenmeyen hata (worker): {}", e.getMessage(), e);
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS);

        log.info("🚀 Rate Calculation Worker başlatıldı (interval: {} ms)", intervalMs);
    }

    // Uygulama kapanışında worker'ı kapatmak için:
    public void shutdownWorker() {
        if (scheduler != null) {
            scheduler.shutdown();
            log.info("🛑 Rate Calculation Worker durduruldu.");
        }
    }



}
