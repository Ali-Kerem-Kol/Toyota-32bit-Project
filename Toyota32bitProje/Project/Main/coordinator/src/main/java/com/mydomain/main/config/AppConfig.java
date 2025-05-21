
package com.mydomain.main.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydomain.main.coordinator.Coordinator;
import com.mydomain.main.coordinator.ICoordinator;
import com.mydomain.main.exception.ProviderConnectionException;
import com.mydomain.main.exception.ProviderInitializationException;
import com.mydomain.main.provider.IProvider;
import com.mydomain.main.service.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import redis.clients.jedis.JedisPool;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public final class AppConfig {

    private static final Logger log = LogManager.getLogger(AppConfig.class);

    private AppConfig() {}

    public static ICoordinator init() {
        log.info("=== AppConfig init started ===");

        ObjectMapper mapper = new ObjectMapper();
        JedisPool jedisPool = new JedisPool(ConfigReader.getRedisHost(), ConfigReader.getRedisPort());

        RedisProducerService rawProducer = new RedisProducerService(
                jedisPool,
                mapper,
                ConfigReader.getRawStreamName(),
                ConfigReader.getStreamMaxLen()
        );

        RedisProducerService calcProducer = new RedisProducerService(
                jedisPool,
                mapper,
                ConfigReader.getCalcStreamName(),
                ConfigReader.getStreamMaxLen()
        );

        RedisConsumerService rawConsumer = new RedisConsumerService(
                jedisPool,
                mapper,
                ConfigReader.getRawStreamName(),
                ConfigReader.getRawGroup(),
                ConfigReader.getRawConsumerName(),
                ConfigReader.getStreamReadCount(),
                ConfigReader.getStreamBlockMillis()
        );

        RedisConsumerService calcConsumer = new RedisConsumerService(
                jedisPool,
                mapper,
                ConfigReader.getCalcStreamName(),
                ConfigReader.getCalcGroup(),
                ConfigReader.getCalcConsumerName(),
                ConfigReader.getStreamReadCount(),
                ConfigReader.getStreamBlockMillis()
        );

        RateCalculatorService calc = new RateCalculatorService();
        KafkaProducerService kafka = new KafkaProducerService(ConfigReader.getKafkaBootstrapServers());

        Coordinator coord = new Coordinator(rawProducer, rawConsumer, calcProducer, calcConsumer, calc, kafka);
        coord.startStreamConsumerLoop();

        JSONArray defs = ConfigReader.getProviders();

        for (int i = 0; i < defs.length(); i++) {
            JSONObject def = defs.getJSONObject(i);
            Thread t = new Thread(() -> startProvider(def, coord), "ProviderThread-" + i);
            t.start();
        }

        return coord;
    }

    private static void startProvider(JSONObject def, ICoordinator coord) {
        String className = def.getString("className");
        String platformName = def.optString("platformName", className);
        log.info("Loading provider => {}", className);

        IProvider provider = instantiateProvider(className);
        setCoordinator(provider, className, coord);

        Map<String, String> paramMap = buildParamMap(def);
        subscribeRatesIfPresent(provider, def, platformName);

        try {
            provider.connect(platformName, paramMap);
        } catch (ProviderConnectionException ce) {
            log.warn("Provider {} failed to connect (auto-reconnect possible): {}", className, ce.getMessage());
        } catch (Exception e) {
            log.error("connect() error in provider {}", className, e);
        }
    }

    private static IProvider instantiateProvider(String fqcn) {
        try {
            return (IProvider) Class.forName(fqcn).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            log.error("Reflection error – cannot instantiate {}", fqcn, e);
            throw new ProviderInitializationException("Cannot instantiate: " + fqcn, e);
        }
    }

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

    private static Map<String, String> buildParamMap(JSONObject obj) {
        Map<String, String> m = new HashMap<>();
        for (String k : obj.keySet()) {
            if (!"className".equals(k) && !"subscribeRates".equals(k) && !"platformName".equals(k)) {
                m.put(k, obj.getString(k));
            }
        }
        return m;
    }

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
