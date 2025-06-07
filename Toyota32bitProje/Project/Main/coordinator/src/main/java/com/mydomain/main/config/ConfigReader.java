package com.mydomain.main.config;

import com.mydomain.main.exception.ConfigLoadException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Uygulamanın yapılandırma dosyası olan config.json'u okur,
 * doğrulama yapar ve uygulama boyunca yapılandırma değerlerine erişim sağlar.
 */
public class ConfigReader {

    private static final Logger log = LogManager.getLogger(ConfigReader.class);
    private static final String CONFIG_FILE_PATH = "/app/Main/coordinator/config/config.json";
    private static JSONObject config;

    /**
     * config.json dosyasını yükler ve zorunlu alanları kontrol eder.
     * Bu metot uygulama başlarken bir kez çağrılmalıdır.
     */
    public static void initConfigs() {
        log.info("🔍 Reading configuration file from: {}", CONFIG_FILE_PATH);
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(CONFIG_FILE_PATH));
            String text = new String(bytes, StandardCharsets.UTF_8);
            config = new JSONObject(text);

            // Zorunlu alanlar kontrolü
            if (!config.has("kafka")) throw new ConfigLoadException("Missing 'kafka' object in config.json!");
            if (!config.has("calculation")) throw new ConfigLoadException("Missing 'calculation' object in config.json!");
            if (!config.has("redis")) throw new ConfigLoadException("Missing 'redis' object in config.json!");
            if (!config.has("providers")) throw new ConfigLoadException("Missing 'providers' array in config.json!");
            if (!config.has("filters")) throw new ConfigLoadException("Missing 'filters' object in config.json!");

            JSONArray providers = config.getJSONArray("providers");
            for (int i = 0; i < providers.length(); i++) {
                JSONObject p = providers.getJSONObject(i);
                if (!p.has("className")) throw new ConfigLoadException("Provider missing 'className' at index " + i);
                if (!p.has("subscribeRates")) throw new ConfigLoadException("Provider missing 'subscribeRates' at index " + i);
            }

            log.info("✅ Configuration loaded and validated successfully.");

        } catch (Exception e) {
            log.fatal("❌ Failed to load/validate config.json from path '{}': {}", CONFIG_FILE_PATH, e.getMessage());
            throw new ConfigLoadException("Failed to load/validate config.json from external path", e);
        }
    }

    /**
     * Ana JSON nesnesine erişim sağlar.
     */
    private static JSONObject getMainConfig() {
        if (config == null) {
            throw new ConfigLoadException("config.json was not loaded. Did you forget to call initConfigs()?");
        }
        return config;
    }

    // ===========================
    // 🚀 Provider Ayarları
    // ===========================

    /** Tüm provider tanımlarını döner. */
    public static JSONArray getProviders() {
        return getMainConfig().getJSONArray("providers");
    }

    /** Tüm provider sınıf adlarını döner. */
    public static JSONArray getProvidersClassName() {
        JSONArray array = new JSONArray();
        JSONArray providers = getProviders();
        for (int i = 0; i < providers.length(); i++) {
            JSONObject p = providers.getJSONObject(i);
            array.put(p.optString("className", ""));
        }
        return array;
    }

    /** Tüm platform adlarını döner. (Yoksa className kullanılır) */
    public static JSONArray getProvidersPlatformName() {
        JSONArray array = new JSONArray();
        JSONArray providers = getProviders();
        for (int i = 0; i < providers.length(); i++) {
            JSONObject p = providers.getJSONObject(i);
            array.put(p.optString("platformName", p.optString("className", "")));
        }
        return array;
    }

    /** Tüm provider'larda abone olunan tüm kurların birleşimini döner. */
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

    // ===========================
    // ⚙️ Kafka Ayarları
    // ===========================

    private static JSONObject getKafkaObject() { return getMainConfig().getJSONObject("kafka"); }

    public static String getKafkaBootstrapServers() { return getKafkaObject().getString("bootstrapServers"); }

    public static String getKafkaTopicName() { return getKafkaObject().getString("topicName"); }

    public static String getKafkaAcks() { return getKafkaObject().getString("acks"); }

    public static int getKafkaRetries() { return getKafkaObject().getInt("retries"); }

    public static int getKafkaDeliveryTimeout() { return getKafkaObject().optInt("deliveryTimeoutMs", 30000); }

    public static int getKafkaRequestTimeout() { return getKafkaObject().optInt("requestTimeoutMs", 15000); }

    public static long getKafkaReinitPeriod() { return getKafkaObject().optLong("reinitPeriodSec", 5); }


    // ===========================
    // 🧮 Hesaplama Ayarları
    // ===========================

    private static JSONObject getCalculationObject() { return getMainConfig().getJSONObject("calculation"); }

    /** Hesaplama metodu: "javascript" gibi */
    public static String getCalculationMethod() { return getCalculationObject().getString("calculationMethod"); }

    /** JavaScript formül dosyasının yolu */
    public static String getFormulaFilePath() { return getCalculationObject().getString("formulaFilePath"); }

    // ===========================
    // 🧠 Redis Ayarları
    // ===========================

    private static JSONObject getRedisObject() { return getMainConfig().getJSONObject("redis"); }

    public static String getRedisHost() { return getRedisObject().getString("host"); }

    public static int getRedisPort() { return getRedisObject().getInt("port"); }

    public static int getRedisTTLSeconds() { return getRedisObject().optInt("ttlSeconds", 3600); }

    public static int getRedisMaxListSize() { return getRedisObject().optInt("maxListSize", 10); }

    // ===========================
    // 🔍 Filtre Ayarları
    // ===========================

    public static JSONObject getFiltersObject() {
        return getMainConfig().getJSONObject("filters");
    }
}
