package com.mydomain.main.config;

import com.mydomain.main.exception.ConfigLoadException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Uygulamanın config.json dosyasındaki ayarları okuyan yardımcı sınıf.
 * <p>
 * Kafka, hesaplama (calculation) ve Redis konfigürasyonlarını sağlar,
 * ayrıca dinamik yüklenecek sağlayıcıları (providers) getirir.
 * </p>
 */
public class ConfigReader {

    private static final Logger logger = LogManager.getLogger(ConfigReader.class);
    private static JSONObject config;

    static {
        try {
            InputStream is = ConfigReader.class.getClassLoader().getResourceAsStream("config.json");
            if (is == null) {
                logger.error("config.json not found in resources!");
                throw new ConfigLoadException("config.json not found in resources!");
            }
            String text = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            config = new JSONObject(text);
            logger.info("Loaded config.json successfully (ConfigReader).");

            if (!config.has("kafka")) {
                logger.error("Missing 'kafka' object in config.json!");
                throw new ConfigLoadException("Missing 'kafka' object in config.json!");
            }
            if (!config.has("calculation")) {
                logger.error("Missing 'calculation' object in config.json!");
                throw new ConfigLoadException("Missing 'calculation' object in config.json!");
            }
            if (!config.has("redis")) {
                logger.error("Missing 'redis' object in config.json!");
                throw new ConfigLoadException("Missing 'redis' object in config.json!");
            }
        } catch (ConfigLoadException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to load config.json => {}", e.getMessage(), e);
            throw new ConfigLoadException("Failed to load/parse config.json", e);
        }
    }


    // Providers

    /**
     * config.json içindeki "providers" dizisini döner.
     *
     * @return Sağlayıcı tanımlarını içeren JSONArray; yoksa boş dizi
     */
    public static JSONArray getProviders() {
        if (!config.has("providers")) {
            logger.warn("No 'providers' array found in config.json; returning empty array.");
            return new JSONArray();
        }
        return config.getJSONArray("providers");
    }

    /**
     * Config dosyasındaki tüm sağlayıcılardan (providers) alınan
     * "subscribeRates" alanlarındaki tam oran adlarını döndürür.
     * <p>
     * Örneğin: "PF1_USDTRY", "PF2_EURUSD" gibi değerler.
     * Aynı oran tekrarlanıyorsa yalnızca bir kez döner.
     * Ekleme sırasına sadık kalınır.
     * </p>
     *
     * @return Tüm sağlayıcılardan alınan benzersiz ve sıralı kur adlarının kümesi
     */
    public static Set<String> getSubscribeRates() {
        Set<String> rateSet = new LinkedHashSet<>();
        JSONArray providers = getProviders();

        for (int i = 0; i < providers.length(); i++) {
            JSONObject provider = providers.getJSONObject(i);
            if (provider.has("subscribeRates")) {
                JSONArray rates = provider.getJSONArray("subscribeRates");
                for (int j = 0; j < rates.length(); j++) {
                    rateSet.add(rates.getString(j));
                }
            }
        }

        return rateSet;
    }

    /**
     * Tüm "subscribeRates" değerlerinin sadece "_" karakterinden sonraki kısmını döndürür.
     * <p>
     * Örneğin: "PF1_USDTRY" → "USDTRY" olarak ayrıştırılır.
     * Kaynak olarak {@link #getSubscribeRates()} metodu kullanılır, böylece tutarlılık sağlanır.
     * Aynı oran tekrarlanıyorsa yalnızca bir kez döner.
     * </p>
     *
     * @return Benzersiz ve sıralı kısa kur adlarının kümesi (örn: "USDTRY", "EURUSD")
     */
    public static Set<String> getSubscribeRatesShort() {
        Set<String> shortRateSet = new LinkedHashSet<>();
        Set<String> allRates = getSubscribeRates();

        for (String fullRate : allRates) {
            String shortRate = fullRate.split("_")[1];
            shortRateSet.add(shortRate);
        }

        return shortRateSet;
    }

    // Kafka

    /**
     * Kafka ayarlarını içeren JSON nesnesini döner.
     *
     * @return Kafka konfigürasyon nesnesi
     * @throws ConfigLoadException Kafka nesnesi eksikse fırlatılır
     */
    private static JSONObject getKafkaObject() {
        if (!config.has("kafka")) {
            logger.error("Missing 'kafka' object in config.json!");
            throw new ConfigLoadException("Missing 'kafka' object in config.json!");
        }
        return config.getJSONObject("kafka");
    }

    /**
     * Kafka bootstrap sunucu adreslerini döner.
     *
     * @return Bootstrap sunucu adresleri (örneğin "localhost:9092")
     */
    public static String getKafkaBootstrapServers() {
        return getKafkaObject().getString("bootstrapServers");
    }

    /**
     * Kafka topic adını döner.
     *
     * @return Kafka topic ismi
     */
    public static String getKafkaTopicName() {
        return getKafkaObject().getString("topicName");
    }

    /**
     * Kafka ACK ayarını döner.
     *
     * @return ACK konfigürasyonu (örneğin "all", "1", "0")
     */
    public static String getKafkaAcks() {
        return getKafkaObject().getString("acks");
    }

    /**
     * Kafka yeniden deneme (retry) sayısını döner.
     *
     * @return Retry sayısı
     */
    public static int getKafkaRetries() {
        return getKafkaObject().getInt("retries");
    }

    public static int getKafkaDeliveryTimeout() {
        return getKafkaObject().optInt("deliveryTimeoutMs", 30_000);
    }

    public static int getKafkaRequestTimeout() {
        return getKafkaObject().optInt("requestTimeoutMs", 15_000);
    }

    public static long getKafkaReinitPeriod() {
        return getKafkaObject().optLong("reinitPeriodSec", 5L);
    }

    public static int getKafkaLingerMs() {
        return getKafkaObject().optInt("lingerMs", 0);   // 0 => hemen gönder
    }

    // Hesaplama

    /**
     * Calculation (hesaplama) ayarlarını içeren JSON nesnesini döner.
     *
     * @return Calculation konfigürasyon nesnesi
     * @throws ConfigLoadException Calculation nesnesi eksikse fırlatılır
     */
    private static JSONObject getCalculationObject() {
        if (!config.has("calculation")) {
            logger.error("Missing 'calculation' object in config.json!");
            throw new ConfigLoadException("Missing 'calculation' object in config.json!");
        }
        return config.getJSONObject("calculation");
    }

    /**
     * Hesaplama yöntemini döner (örn. "javascript").
     *
     * @return Calculation method adı
     */
    public static String getCalculationMethod() {
        return getCalculationObject().getString("calculationMethod");
    }

    /**
     * Harici formül dosyasının yolunu döner.
     *
     * @return Formül dosyası yolu
     */
    public static String getFormulaFilePath() {
        return getCalculationObject().getString("formulaFilePath");
    }

    // Redis

    /**
     * Redis ayarlarını içeren JSON nesnesini döner.
     *
     * @return Redis konfigürasyon nesnesi
     * @throws ConfigLoadException Redis nesnesi eksikse fırlatılır
     */
    private static JSONObject getRedisObject() {
        if (!config.has("redis")) {
            logger.error("Missing 'redis' object in config.json!");
            throw new ConfigLoadException("Missing 'redis' object in config.json!");
        }
        return config.getJSONObject("redis");
    }

    /**
     * Redis sunucu host adresini döner.
     *
     * @return Redis host (örneğin "localhost")
     */
    public static String getRedisHost() {
        return getRedisObject().getString("host");
    }

    /**
     * Redis sunucu port numarasını döner.
     *
     * @return Redis port (örneğin 6379)
     */
    public static int getRedisPort() {
        return getRedisObject().getInt("port");
    }

    /**
     * Redis veritabanı numarasını döner.
     *
     * @return Redis database indeksi (varsayılan 0)
     */
    public static int getRedisDatabase() {
        return getRedisObject().optInt("database", 0);
    }

    /**
     * Redis şifre bilgisini döner.
     *
     * @return Redis password (boş ise şifresiz bağlantı)
     */
    public static String getRedisPassword() {
        return getRedisObject().optString("password", "");
    }

    /**
     * Redis key’lerinin ne kadar süre sonra otomatik silineceğini (saniye cinsinden) döner.
     *
     * @return raw: prefix’li key’ler için TTL (saniye)
     */
    public static int getRawTtl() {
        return getRedisObject().getInt("rawTtl");
    }

    /**
     * Redis key’lerinin ne kadar süre sonra otomatik silineceğini (saniye cinsinden) döner.
     *
     * @return calculated: prefix’li key’ler için TTL (saniye)
     */
    public static int getCalculatedTtl() {
        return getRedisObject().getInt("calculatedTtl");
    }


    // Stream ayarları
    public static String getRawStreamName() {
        return getRedisObject().optString("rawStream", "raw_rates");
    }

    public static String getCalcStreamName() {
        return getRedisObject().optString("calculatedStream", "calculated_rates");
    }

    public static long getStreamMaxLen() {
        return getRedisObject().optLong("streamMaxLen", 10000);
    }

    public static String getRawGroup() { return getRedisObject().optString("rawGroup", "raw-group"); }

    public static String getCalcGroup() { return getRedisObject().optString("calcGroup", "calc-group"); }

    public static String getRawConsumerName() { return getRedisObject().optString("rawConsumer", "raw-consumer"); }

    public static String getCalcConsumerName() { return getRedisObject().optString("calcConsumer", "calc-consumer"); }

    public static int getStreamReadCount() {
        return getRedisObject().optInt("streamReadCount", 10);  // varsayılan: 10 kayıt
    }

    public static int getStreamBlockMillis() {
        return getRedisObject().optInt("streamBlockMillis", 5000); // varsayılan: 5 saniye
    }


}
