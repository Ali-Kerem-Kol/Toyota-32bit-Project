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
 * Uygulamanın ana bileşenlerini başlatan konfigürasyon sınıfı.
 * <p>
 * Bu sınıf; Redis servisi, Rate hesaplayıcı servisi ve Kafka üretici servisini
 * oluşturur, ardından config.json'daki sağlayıcı tanımlarına göre
 * IProvider implementasyonlarını dinamik olarak yükleyip başlatır.
 * </p>
 */
public class AppConfig {

    private static final Logger logger = LogManager.getLogger(AppConfig.class);

    /**
     * Uygulamanın koordinatörünü başlatır ve döner.
     * <p>
     * - KafkaProducerService oluşturur<br>
     * - RedisService oluşturur<br>
     * - RateCalculatorService oluşturur<br>
     * - Coordinator nesnesini bu bileşenlerle inşa eder<br>
     * - config.json'daki provider tanımlarını okuyup her biri için ayrı bir thread başlatır
     * </p>
     *
     * @return Başlatılmış ICoordinator örneği
     */
    public static ICoordinator init() {
        logger.info("=== AppConfig init started ===");

        // RedisService oluşturuluyor
        RedisService redisService = new RedisService(ConfigReader.getRedisHost(), ConfigReader.getRedisPort());

        // RateCalculatorService oluşturuluyor
        RateCalculatorService rateCalculatorService = new RateCalculatorService(redisService);

        // KafkaProducerService oluşturuluyor
        KafkaProducerService kafkaProducerService = new KafkaProducerService(ConfigReader.getKafkaBootstrapServers(),redisService);

        // Coordinator oluşturuluyor
        Coordinator coordinator = new Coordinator(redisService, rateCalculatorService, kafkaProducerService);

        // config.json'daki provider tanımlarını yükle
        JSONArray providersArray = ConfigReader.getProviders();
        List<Thread> providerThreads = new ArrayList<>();

        for (int i = 0; i < providersArray.length(); i++) {
            JSONObject providerObj = providersArray.getJSONObject(i);
            Thread t = new Thread(() -> {
                String className = providerObj.getString("className");
                logger.info("Loading provider => {}", className);

                // 1) Reflection ile provider örneği oluştur
                IProvider provider = instantiateProvider(className);

                // 2) Coordinator referansı set et
                setCoordinatorMethod(provider, className, coordinator);

                // 3) Parametre haritasını oluştur
                Map<String, String> paramMap = buildParamMap(providerObj);
                String platformName = providerObj.optString("platformName", className);

                // 4) Provider.connect çağrısı
                try {
                    provider.connect(platformName, paramMap);
                } catch (ProviderConnectionException ce) {
                    logger.warn("Provider {} failed to connect. It will attempt auto-reconnect. Msg: {}",
                            className, ce.getMessage());
                } catch (Exception e) {
                    logger.error("Generic error connecting provider {} => {}", className, e.getMessage(), e);
                }

                // 5) Abonelikleri işle
                subscribeRatesIfPresent(provider, providerObj, className);
            });
            t.setName("ProviderThread-" + i);
            providerThreads.add(t);
            t.start();
        }

        return coordinator;
    }

    /**
     * Sağlayıcı sınıfını reflection ile örnekler.
     *
     * @param className Tam nitelikli sınıf adı
     * @return IProvider örneği
     * @throws ProviderInitializationException Örneklendirme başarısızsa
     */
    private static IProvider instantiateProvider(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            return (IProvider) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            logger.error("Reflection error: failed to instantiate provider {}", className);
            throw new ProviderInitializationException("Could not instantiate provider: " + className);
        }
    }

    /**
     * Sağlayıcıya Coordinator örneğini set eden yöntem.
     *
     * @param provider    Yüklenen provider örneği
     * @param className   Sağlayıcı sınıfının adı (log için)
     * @param coordinator Coordinator örneği
     * @throws ProviderInitializationException Coordinator set edilemezse
     */
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

    /**
     * JSON objesinden sınıfa parametre haritası oluşturur.
     *
     * @param providerObj Provider tanımı içeren JSONObject
     * @return Key-value çiftlerinden oluşan parametre haritası
     */
    private static Map<String, String> buildParamMap(JSONObject providerObj) {
        Map<String, String> paramMap = new HashMap<>();
        for (String key : providerObj.keySet()) {
            if (!"className".equals(key) && !"subscribeRates".equals(key)) {
                paramMap.put(key, providerObj.getString(key));
            }
        }
        return paramMap;
    }

    /**
     * Eğer providerObj içinde "subscribeRates" varsa, bu rate'lere abone olur.
     *
     * @param provider    IProvider örneği
     * @param providerObj Provider tanımı içeren JSONObject
     * @param className   Sağlayıcı sınıfının adı (log için)
     */
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
