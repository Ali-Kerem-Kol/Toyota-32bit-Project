package com.mydomain.main.config;

import com.mydomain.main.service.RateCalculatorService;
import com.mydomain.main.service.RedisService;
import com.mydomain.main.coordinator.Coordinator;
import com.mydomain.main.coordinator.ICoordinator;
import com.mydomain.main.exception.KafkaPublishingException;
import com.mydomain.main.exception.ProviderConnectionException;
import com.mydomain.main.exception.ProviderInitializationException;
import com.mydomain.main.provider.IProvider;
import com.mydomain.main.service.KafkaProducerService;
import org.apache.kafka.common.KafkaException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AppConfig is responsible for initializing the main Coordinator,
 * Kafka Producer, and dynamically loading Providers based on config.json.
 */
public class AppConfig {

    private static final Logger logger = LogManager.getLogger(AppConfig.class);

    public static ICoordinator init() {
        logger.info("=== AppConfig init started ===");

        // Kafka producer oluşturuluyor.
        KafkaProducerService kafkaProducerService = createKafkaProducer();

        // Redis ayarlarını config.json'dan okuyarak RedisCacheManager örneğini oluşturuyoruz.
        String redisHost = ConfigReader.getRedisHost();
        int redisPort = ConfigReader.getRedisPort();
        // İstersek database ve password da eklenebilir, burada temel haliyle oluşturuluyor.
        RedisService redisService = new RedisService(redisHost, redisPort);

        RateCalculatorService rateCalculatorService = new RateCalculatorService();

        // Coordinator, Kafka producer ve RedisCacheManager ile oluşturuluyor.
        Coordinator coordinator = new Coordinator(redisService,rateCalculatorService, kafkaProducerService);

        // Provider bilgileri config.json'dan okunuyor.
        JSONArray providersArray = ConfigReader.getProviders();
        List<Thread> providerThreads = new ArrayList<>();

        // Her provider için ayrı bir thread oluşturuluyor.
        for (int i = 0; i < providersArray.length(); i++) {
            JSONObject providerObj = providersArray.getJSONObject(i);
            Thread t = new Thread(() -> {
                String className = providerObj.getString("className");
                logger.info("Loading provider => {}", className);

                // 1) Provider Reflection: Provider instance oluşturuluyor.
                IProvider provider = instantiateProvider(className);

                // 2) Coordinator referansı set ediliyor.
                setCoordinatorMethod(provider, className, coordinator);

                // 3) Param map oluşturuluyor.
                Map<String, String> paramMap = buildParamMap(providerObj);
                String platformName = providerObj.optString("platformName", className);

                // 4) Provider bağlantısı deneniyor.
                try {
                    provider.connect(platformName, paramMap);
                } catch (ProviderConnectionException ce) {
                    logger.warn("Provider {} failed to connect. It will attempt auto-reconnect. Msg: {}",
                            className, ce.getMessage());
                } catch (Exception e) {
                    logger.error("Generic error connecting provider {} => {}", className, e.getMessage(), e);
                }

                // 5) Subscribe işlemleri yapılıyor.
                subscribeRatesIfPresent(provider, providerObj, className);
            });
            t.setName("ProviderThread-" + i);
            providerThreads.add(t);
            t.start();
        }

        return coordinator;
    }

    private static KafkaProducerService createKafkaProducer() {
        try {
            String bootstrap = ConfigReader.getKafkaBootstrapServers();
            return new KafkaProducerService(bootstrap);
        } catch (KafkaException e) {
            logger.error("Failed to create Kafka producer => {}", e.getMessage(), e);
            throw new KafkaPublishingException("Error creating Kafka producer", e);
        }
    }

    private static IProvider instantiateProvider(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            return (IProvider) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            logger.error("Reflection error: failed to instantiate provider {}", className, e);
            throw new ProviderInitializationException("Could not instantiate provider: " + className, e);
        }
    }

    private static void setCoordinatorMethod(IProvider provider, String className, ICoordinator coordinator) {
        try {
            Method setCoord = provider.getClass().getMethod("setCoordinator", ICoordinator.class);
            setCoord.invoke(provider, coordinator);
        } catch (NoSuchMethodException nsme) {
            logger.warn("No setCoordinator(CoordinatorInterface) found in {}", className);
        } catch (Exception e) {
            logger.error("Failed to set coordinator for provider {} => {}", className, e.getMessage(), e);
            throw new ProviderInitializationException("Error setting coordinator for " + className, e);
        }
    }

    private static Map<String, String> buildParamMap(JSONObject providerObj) {
        Map<String, String> paramMap = new HashMap<>();
        for (String key : providerObj.keySet()) {
            if (!"className".equals(key) && !"subscribeRates".equals(key)) {
                paramMap.put(key, providerObj.getString(key));
            }
        }
        return paramMap;
    }

    private static void subscribeRatesIfPresent(IProvider provider, JSONObject providerObj, String className) {
        if (!providerObj.has("subscribeRates")) {
            return;
        }
        String platformName = providerObj.optString("platformName", className);
        JSONArray arr = providerObj.getJSONArray("subscribeRates");
        List<String> subscribeRates = new ArrayList<>();
        for (int j = 0; j < arr.length(); j++) {
            subscribeRates.add(arr.getString(j));
        }
        for (String rateName : subscribeRates) {
            try {
                provider.subscribe(platformName, rateName);
            } catch (Exception e) {
                logger.error("Error subscribing rate {} for provider {} => {}", rateName, className, e.getMessage(), e);
            }
        }
    }

}
