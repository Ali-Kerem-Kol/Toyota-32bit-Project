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
 * {@code ConfigReader}, uygulamanın yapılandırma dosyası olan `config.json` dosyasını okur,
 * zorunlu alanların varlığını ve geçerliliğini kontrol eder, ardından uygulama boyunca
 * yapılandırma değerlerine erişim sağlamak için statik metodlar sunar. Tek bir global
 * konfigürasyon nesnesi (`config`) tutar ve bu nesneye erişimi yönetir.
 *
 * <p>Hizmetin temel işleyişi:
 * <ul>
 *   <li>`initConfigs` metodu, uygulama başlarken bir kez çağrılarak konfigürasyonu yükler.</li>
 *   <li>Zorunlu alanlar (kafka, calculation, redis, providers, filters) kontrol edilir.</li>
 *   <li>Provider tanımlarında `className` ve `subscribeRates` alanları doğrulanır.</li>
 *   <li>Konfigürasyon değerleri, Kafka, Redis, hesaplama ve filtre ayarları için ayrı metodlarla erişilir.</li>
 * </ul>
 * </p>
 *
 * <p><b>Özellikler:</b>
 * <ul>
 *   <li>Statik bir yapı ile tek bir konfigürasyon örneği sağlar.</li>
 *   <li>Loglama için Apache Log4j ile hata ayıklama ve izleme seviyeleri desteklenir.</li>
 *   <li>Hata durumunda `ConfigLoadException` fırlatılarak erken doğrulama yapılır.</li>
 * </ul>
 * </p>
 *
 * @author Ali Kerem Kol
 * @version 1.0
 * @since 2025-06-07
 */
public class ConfigReader {

    private static final Logger log = LogManager.getLogger(ConfigReader.class);
    private static final String CONFIG_FILE_PATH = "/app/Main/coordinator/config/config.json";
    private static JSONObject config;

    /**
     * `config.json` dosyasını yükler ve zorunlu alanların varlığını kontrol eder.
     * Bu metot, uygulama başlarken bir kez çağrılmalıdır. Başarısız yükleme durumunda
     * `ConfigLoadException` fırlatılır.
     *
     * @throws ConfigLoadException Dosya okunamazsa, JSON geçersizse veya zorunlu alanlar eksikse
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
     * Ana JSON konfigürasyon nesnesine erişim sağlar.
     * `initConfigs` çağrılmadan önce erişim sağlanırsa hata fırlatır.
     *
     * @return Yüklü konfigürasyon nesnesi
     * @throws ConfigLoadException `initConfigs` çağrılmadıysa
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

    /**
     * Tüm provider tanımlarını içeren JSON dizisini döner.
     *
     * @return Provider tanımlarının dizisi
     * @throws ConfigLoadException `initConfigs` çağrılmadıysa
     */
    public static JSONArray getProviders() {
        return getMainConfig().getJSONArray("providers");
    }

    /**
     * Tüm provider sınıf adlarını içeren bir JSON dizisi döner.
     *
     * @return Provider sınıf adlarının dizisi
     * @throws ConfigLoadException `initConfigs` çağrılmadıysa
     */
    public static JSONArray getProvidersClassName() {
        JSONArray array = new JSONArray();
        JSONArray providers = getProviders();
        for (int i = 0; i < providers.length(); i++) {
            JSONObject p = providers.getJSONObject(i);
            array.put(p.optString("className", ""));
        }
        return array;
    }

    /**
     * Tüm platform adlarını içeren bir JSON dizisi döner.
     * Eğer `platformName` yoksa `className` kullanılır.
     *
     * @return Platform adlarının dizisi
     * @throws ConfigLoadException `initConfigs` çağrılmadıysa
     */
    public static JSONArray getProvidersPlatformName() {
        JSONArray array = new JSONArray();
        JSONArray providers = getProviders();
        for (int i = 0; i < providers.length(); i++) {
            JSONObject p = providers.getJSONObject(i);
            array.put(p.optString("platformName", p.optString("className", "")));
        }
        return array;
    }

    /**
     * Tüm provider’larda abone olunan tüm kurların birleşimini döner.
     * Yinelenenler kaldırılır.
     *
     * @return Abone olunan kurların birleşik kümesi
     * @throws ConfigLoadException `initConfigs` çağrılmadıysa
     */
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

    /**
     * Kafka ayarlarını içeren JSON nesnesine erişim sağlar.
     *
     * @return Kafka konfigürasyonu
     * @throws ConfigLoadException `initConfigs` çağrılmadıysa
     */
    private static JSONObject getKafkaObject() { return getMainConfig().getJSONObject("kafka"); }

    /**
     * Kafka bootstrap server adreslerini döner.
     *
     * @return Bootstrap server adresleri
     * @throws ConfigLoadException `initConfigs` çağrılmadıysa veya `kafka` nesnesi eksikse
     */
    public static String getKafkaBootstrapServers() { return getKafkaObject().getString("bootstrapServers"); }

    /**
     * Kafka topic adını döner.
     *
     * @return Topic adı
     * @throws ConfigLoadException `initConfigs` çağrılmadıysa veya `kafka` nesnesi eksikse
     */
    public static String getKafkaTopicName() { return getKafkaObject().getString("topicName"); }

    /**
     * Kafka acks ayarını döner.
     *
     * @return Acks değeri (örneğin "all")
     * @throws ConfigLoadException `initConfigs` çağrılmadıysa veya `kafka` nesnesi eksikse
     */
    public static String getKafkaAcks() { return getKafkaObject().getString("acks"); }

    /**
     * Kafka yeniden deneme sayısını döner.
     *
     * @return Yeniden deneme sayısı
     * @throws ConfigLoadException `initConfigs` çağrılmadıysa veya `kafka` nesnesi eksikse
     */
    public static int getKafkaRetries() { return getKafkaObject().getInt("retries"); }

    /**
     * Kafka teslim zaman aşımı süresini döner (milisaniye cinsinden).
     *
     * @return Teslim zaman aşımı (varsayılan: 30000 ms)
     * @throws ConfigLoadException `initConfigs` çağrılmadıysa veya `kafka` nesnesi eksikse
     */
    public static int getKafkaDeliveryTimeout() { return getKafkaObject().optInt("deliveryTimeoutMs", 30000); }

    /**
     * Kafka istek zaman aşımı süresini döner (milisaniye cinsinden).
     *
     * @return İstek zaman aşımı (varsayılan: 15000 ms)
     * @throws ConfigLoadException `initConfigs` çağrılmadıysa veya `kafka` nesnesi eksikse
     */
    public static int getKafkaRequestTimeout() { return getKafkaObject().optInt("requestTimeoutMs", 15000); }

    /**
     * Kafka yeniden başlatma periyodunu döner (saniye cinsinden).
     *
     * @return Yeniden başlatma periyodu (varsayılan: 5 saniye)
     * @throws ConfigLoadException `initConfigs` çağrılmadıysa veya `kafka` nesnesi eksikse
     */
    public static long getKafkaReinitPeriod() { return getKafkaObject().optLong("reinitPeriodSec", 5); }


    // ===========================
    // 🧮 Hesaplama Ayarları
    // ===========================

    /**
     * Hesaplama ayarlarını içeren JSON nesnesine erişim sağlar.
     *
     * @return Hesaplama konfigürasyonu
     * @throws ConfigLoadException `initConfigs` çağrılmadıysa veya `calculation` nesnesi eksikse
     */
    private static JSONObject getCalculationObject() { return getMainConfig().getJSONObject("calculation"); }

    /**
     * Hesaplama metodunu döner (örneğin, "javascript").
     *
     * @return Hesaplama metodu
     * @throws ConfigLoadException `initConfigs` çağrılmadıysa veya `calculation` nesnesi eksikse
     */
    public static String getCalculationMethod() { return getCalculationObject().getString("calculationMethod"); }

    /**
     * JavaScript formül dosyasının yolunu döner.
     *
     * @return Formül dosyasının yolu
     * @throws ConfigLoadException `initConfigs` çağrılmadıysa veya `calculation` nesnesi eksikse
     */
    public static String getFormulaFilePath() { return getCalculationObject().getString("formulaFilePath"); }

    // ===========================
    // 🧠 Redis Ayarları
    // ===========================

    /**
     * Redis ayarlarını içeren JSON nesnesine erişim sağlar.
     *
     * @return Redis konfigürasyonu
     * @throws ConfigLoadException `initConfigs` çağrılmadıysa veya `redis` nesnesi eksikse
     */
    private static JSONObject getRedisObject() { return getMainConfig().getJSONObject("redis"); }

    /**
     * Redis host adresini döner.
     *
     * @return Redis host adresi
     * @throws ConfigLoadException `initConfigs` çağrılmadıysa veya `redis` nesnesi eksikse
     */
    public static String getRedisHost() { return getRedisObject().getString("host"); }

    /**
     * Redis port numarasını döner.
     *
     * @return Redis portu
     * @throws ConfigLoadException `initConfigs` çağrılmadıysa veya `redis` nesnesi eksikse
     */
    public static int getRedisPort() { return getRedisObject().getInt("port"); }

    /**
     * Redis TTL süresini döner (saniye cinsinden).
     *
     * @return TTL süresi (varsayılan: 3600 saniye)
     * @throws ConfigLoadException `initConfigs` çağrılmadıysa veya `redis` nesnesi eksikse
     */
    public static int getRedisTTLSeconds() { return getRedisObject().optInt("ttlSeconds", 3600); }

    /**
     * Redis liste maksimum boyutunu döner.
     *
     * @return Maksimum liste boyutu (varsayılan: 10)
     * @throws ConfigLoadException `initConfigs` çağrılmadıysa veya `redis` nesnesi eksikse
     */
    public static int getRedisMaxListSize() { return getRedisObject().optInt("maxListSize", 10); }

    // ===========================
    // 🔍 Filtre Ayarları
    // ===========================

    /**
     * Filtre ayarlarını içeren JSON nesnesine erişim sağlar.
     *
     * @return Filtre konfigürasyonu
     * @throws ConfigLoadException `initConfigs` çağrılmadıysa veya `filters` nesnesi eksikse
     */
    public static JSONObject getFiltersObject() {
        return getMainConfig().getJSONObject("filters");
    }
}
