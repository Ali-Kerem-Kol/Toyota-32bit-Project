package com.mydomain.main.config;

import com.mydomain.main.exception.ConfigLoadException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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

}
