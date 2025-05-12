package com.mydomain.main.config;

import com.mydomain.main.coordinator.Coordinator;
import com.mydomain.main.coordinator.ICoordinator;
import com.mydomain.main.exception.ProviderConnectionException;
import com.mydomain.main.exception.ProviderInitializationException;
import com.mydomain.main.provider.IProvider;
import com.mydomain.main.service.KafkaProducerService;
import com.mydomain.main.service.RateCalculatorService;
import com.mydomain.main.service.RedisService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Uygulamanın tüm bileşenlerini ayağa kaldırır ve sağlayıcıları
 * (IProvider) dinamik olarak başlatır.
 *
 * Değişiklikler / iyileştirmeler:
 *   • subscribe → connect sırası düzeltildi
 *   • stack-trace’ler tam log’lanıyor
 *   • yardımcı metotlar küçükleştirildi
 */
public final class AppConfig {

    private static final Logger log = LogManager.getLogger(AppConfig.class);

    private AppConfig() {

    }

    /** Ana bootstrap */
    public static ICoordinator init() {

        log.info("=== AppConfig init started ===");

        /*Core servisler*/
        RedisService           redis  = new RedisService(ConfigReader.getRedisHost(), ConfigReader.getRedisPort());
        RateCalculatorService  calc   = new RateCalculatorService(redis);
        KafkaProducerService   kafka  = new KafkaProducerService(ConfigReader.getKafkaBootstrapServers(), redis);
        Coordinator            coord  = new Coordinator(redis, calc, kafka);

        /*Provider tanımlarını oku*/
        JSONArray defs = ConfigReader.getProviders();

        for (int i = 0; i < defs.length(); i++) {
            JSONObject def = defs.getJSONObject(i);
            Thread t = new Thread(() -> startProvider(def, coord), "ProviderThread-" + i);
            t.start();
        }

        return coord;
    }

    /*Bir provider’ı başlatır*/
    private static void startProvider(JSONObject def, ICoordinator coord) {

        String className    = def.getString("className");
        String platformName = def.optString("platformName", className);
        log.info("Loading provider => {}", className);

        IProvider provider = instantiateProvider(className);
        setCoordinator(provider, className, coord);

        Map<String,String> paramMap = buildParamMap(def);

        /* 1) Abonelikleri ÖNCE ekliyoruz (soket henüz yoksa bile) */
        subscribeRatesIfPresent(provider, def, platformName);

        /* 2) Ardından connect (bloklu veya non-bloklu olabilir) */
        try {
            provider.connect(platformName, paramMap);
        } catch (ProviderConnectionException ce) {
            log.warn("Provider {} failed to connect (auto-reconnect possible): {}", className, ce.getMessage());
        } catch (Exception e) {
            log.error("connect() error in provider {}", className, e);
        }
    }

    /*Reflection helper*/
    private static IProvider instantiateProvider(String fqcn) {
        try {
            return (IProvider) Class.forName(fqcn).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            log.error("Reflection error – cannot instantiate {}", fqcn, e);
            throw new ProviderInitializationException("Cannot instantiate: " + fqcn, e);
        }
    }

    /*Coordinator inject*/
    private static void setCoordinator(IProvider p, String name, ICoordinator c) {
        try {
            Method m = p.getClass().getMethod("setCoordinator", ICoordinator.class);
            m.invoke(p, c);
        } catch (NoSuchMethodException ns) {
            log.warn("{} has no setCoordinator(..) method", name);
        } catch (Exception e) {
            log.error("setCoordinator failed for {}", name, e);
            throw new ProviderInitializationException("setCoordinator error: " + name, e);
        }
    }

    /*JSON param -> Map*/
    private static Map<String,String> buildParamMap(JSONObject obj) {
        Map<String,String> m = new HashMap<>();
        for (String k : obj.keySet()) {
            if (!"className".equals(k) && !"subscribeRates".equals(k) && !"platformName".equals(k)) {
                m.put(k, obj.getString(k));
            }
        }
        return m;
    }

    /*Abonelikleri uygula*/
    private static void subscribeRatesIfPresent(IProvider p, JSONObject def, String platform) {
        if (!def.has("subscribeRates")) return;

        JSONArray arr = def.getJSONArray("subscribeRates");
        for (int i = 0; i < arr.length(); i++) {
            String rate = arr.getString(i);
            try {
                p.subscribe(platform, rate);
            } catch (Exception e) {
                log.error("subscribe({}) failed for provider {}", rate, def.getString("className"), e);
            }
        }
    }
}
