package com.mydomain.main.config;

import com.mydomain.main.exception.ConfigLoadException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * ConfigReader, resources içindeki config.json dosyasını okuyarak,
 * Kafka, calculation ve Redis ayarlarını sağlayan metodları sunar.
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


    private static JSONObject getKafkaObject() {
        if (!config.has("kafka")) {
            logger.error("Missing 'kafka' object in config.json!");
            throw new ConfigLoadException("Missing 'kafka' object in config.json!");
        }
        return config.getJSONObject("kafka");
    }

    private static JSONObject getRedisObject() {
        if (!config.has("redis")) {
            logger.error("Missing 'redis' object in config.json!");
            throw new ConfigLoadException("Missing 'redis' object in config.json!");
        }
        return config.getJSONObject("redis");
    }

    public static JSONArray getProviders() {
        if (!config.has("providers")) {
            logger.warn("No 'providers' array found in config.json; returning empty array.");
            return new JSONArray();
        }
        return config.getJSONArray("providers");
    }

    private static JSONObject getCalculationObject() {
        if (!config.has("calculation")) {
            logger.error("Missing 'calculation' object in config.json!");
            throw new ConfigLoadException("Missing 'calculation' object in config.json!");
        }
        return config.getJSONObject("calculation");
    }


    public static String getKafkaBootstrapServers() {
        return getKafkaObject().getString("bootstrapServers");
    }

    public static String getKafkaTopicName() {
        return getKafkaObject().getString("topicName");
    }

    public static String getKafkaAcks() {
        return getKafkaObject().getString("acks");
    }

    public static int getKafkaRetries() {
        return getKafkaObject().getInt("retries");
    }

    public static String getCalculationMethod() {
        return getCalculationObject().getString("calculationMethod");
    }

    public static String getFormulaFilePath() {
        return getCalculationObject().getString("formulaFilePath");
    }

    public static String getRedisHost() {
        return getRedisObject().getString("host");
    }

    public static int getRedisPort() {
        return getRedisObject().getInt("port");
    }

    public static int getRedisDatabase() {
        return getRedisObject().optInt("database", 0);
    }

    public static String getRedisPassword() {
        return getRedisObject().optString("password", "");
    }


}
