package com.mydomain.main.config;

import com.mydomain.main.exception.ConfigLoadException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;

/**
 * Uygulamanın yalnızca config.json dosyasını okur ve doğrulama işlemlerini yapar.
 */
public class ConfigReader {

    private static final Logger logger = LogManager.getLogger(ConfigReader.class);
    private static final String CONFIG_FILE_PATH = "/app/Main/coordinator/config/config.json";
    private static JSONObject config;

    public static void initConfigs() {
        logger.info("🔍 Reading configuration file from: {}", CONFIG_FILE_PATH);
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(CONFIG_FILE_PATH));
            String text = new String(bytes, StandardCharsets.UTF_8);
            config = new JSONObject(text);

            // Zorunlu alanlar kontrolü
            if (!config.has("kafka")) throw new ConfigLoadException("Missing 'kafka' object in config.json!");
            if (!config.has("calculation")) throw new ConfigLoadException("Missing 'calculation' object in config.json!");
            if (!config.has("redis")) throw new ConfigLoadException("Missing 'redis' object in config.json!");
            if (!config.has("cache")) throw new ConfigLoadException("Missing 'cache' object in config.json!");
            if (!config.has("providers")) throw new ConfigLoadException("Missing 'providers' array in config.json!");

            JSONArray providers = config.getJSONArray("providers");
            for (int i = 0; i < providers.length(); i++) {
                JSONObject p = providers.getJSONObject(i);
                if (!p.has("className")) throw new ConfigLoadException("Provider missing 'className' at index " + i);
                if (!p.has("subscribeRates")) throw new ConfigLoadException("Provider missing 'subscribeRates' at index " + i);
            }

            logger.info("✅ Configuration loaded and validated successfully.");

        } catch (Exception e) {
            logger.fatal("❌ Failed to load/validate config.json from path '{}': {}", CONFIG_FILE_PATH, e.getMessage());
            throw new ConfigLoadException("Failed to load/validate config.json from external path", e);
        }
    }

    private static JSONObject getMainConfig() {
        if (config == null) {
            throw new ConfigLoadException("config.json was not loaded. Did you forget to call initConfigs()?");
        }
        return config;
    }

    // Provider configuration
    public static JSONArray getProviders() {
        return getMainConfig().getJSONArray("providers");
    }

    public static JSONArray getProvidersClassName() {
        JSONArray array = new JSONArray();
        JSONArray providers = getProviders();
        for (int i = 0; i < providers.length(); i++) {
            JSONObject p = providers.getJSONObject(i);
            array.put(p.optString("className", ""));
        }
        return array;
    }

    public static JSONArray getProvidersPlatformName() {
        JSONArray array = new JSONArray();
        JSONArray providers = getProviders();
        for (int i = 0; i < providers.length(); i++) {
            JSONObject p = providers.getJSONObject(i);
            array.put(p.optString("platformName", p.optString("className", "")));
        }
        return array;
    }

    public static Set<String> getSubscribeRates() {
        Set<String> rateSet = new LinkedHashSet<>();
        JSONArray providers = getProviders();
        for (int i = 0; i < providers.length(); i++) {
            JSONArray rates = providers.getJSONObject(i).optJSONArray("subscribeRates");
            if (rates != null) {
                for (int j = 0; j < rates.length(); j++) {
                    rateSet.add(rates.getString(j));
                }
            }
        }
        return rateSet;
    }

    public static Set<String> getSubscribeRatesShort() {
        Set<String> shortSet = new LinkedHashSet<>();
        for (String full : getSubscribeRates()) {
            if (full.contains("_")) shortSet.add(full.split("_")[1]);
        }
        return shortSet;
    }

    // Kafka configuration
    private static JSONObject getKafkaObject() { return getMainConfig().getJSONObject("kafka"); }
    public static String getKafkaBootstrapServers() { return getKafkaObject().getString("bootstrapServers"); }
    public static String getKafkaTopicName() { return getKafkaObject().getString("topicName"); }
    public static String getKafkaAcks() { return getKafkaObject().getString("acks"); }
    public static int getKafkaRetries() { return getKafkaObject().getInt("retries"); }
    public static int getKafkaDeliveryTimeout() { return getKafkaObject().optInt("deliveryTimeoutMs", 30000); }
    public static int getKafkaRequestTimeout() { return getKafkaObject().optInt("requestTimeoutMs", 15000); }
    public static long getKafkaReinitPeriod() { return getKafkaObject().optLong("reinitPeriodSec", 5); }
    public static int getKafkaLingerMs() { return getKafkaObject().optInt("lingerMs", 0); }

    // Calculation configuration
    private static JSONObject getCalculationObject() { return getMainConfig().getJSONObject("calculation"); }
    public static String getCalculationMethod() { return getCalculationObject().getString("calculationMethod"); }
    public static String getFormulaFilePath() { return getCalculationObject().getString("formulaFilePath"); }

    // Redis configuration
    private static JSONObject getRedisObject() { return getMainConfig().getJSONObject("redis"); }
    public static String getRedisHost() { return getRedisObject().getString("host"); }
    public static int getRedisPort() { return getRedisObject().getInt("port"); }
    public static String getRawStreamName() { return getRedisObject().optString("rawStream", "raw_rates"); }
    public static String getCalcStreamName() { return getRedisObject().optString("calculatedStream", "calculated_rates"); }
    public static long getStreamMaxLen() { return getRedisObject().optLong("streamMaxLen", 10000); }
    public static Duration getStreamRetention() { return Duration.ofSeconds(getRedisObject().optLong("streamRetentionSeconds", 3600)); }
    public static String getRawGroup() { return getRedisObject().optString("rawGroup", "raw-group"); }
    public static String getCalcGroup() { return getRedisObject().optString("calcGroup", "calc-group"); }
    public static String getRawConsumerName() { return getRedisObject().optString("rawConsumer", "raw-consumer"); }
    public static String getCalcConsumerName() { return getRedisObject().optString("calcConsumer", "calc-consumer"); }
    public static int getStreamReadCount() { return getRedisObject().optInt("streamReadCount", 10); }
    public static int getStreamBlockMillis() { return getRedisObject().optInt("streamBlockMillis", 5000); }

    // Cache configuration
    public static JSONObject getCacheObject() { return getMainConfig().getJSONObject("cache"); }
    public static JSONObject getFiltersObject() { return getCacheObject().getJSONObject("filters"); }
    public static int getCacheSize() { return getCacheObject().optInt("size", 10); }
}
