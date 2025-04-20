package com.mydomain.consumer_elasticsearch.config;

import com.mydomain.consumer_elasticsearch.exception.ConfigLoadException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * ConfigReader, resources içindeki config.json dosyasını okuyarak,
 * Kafka ve Elasticsearch ayarlarını sağlayan metodları sunar.
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
            if (!config.has("elasticsearch")) {
                logger.error("Missing 'elasticsearch' object in config.json!");
                throw new ConfigLoadException("Missing 'elasticsearch' object in config.json!");
            }

        } catch (ConfigLoadException e) {
            // Yakalayıp tekrar fırlatıyoruz
            throw e;
        } catch (Exception e) {
            logger.error("Failed to load config.json => {}", e.getMessage(), e);
            throw new ConfigLoadException("Failed to load/parse config.json", e);
        }
    }

    private static JSONObject getKafkaObject() {
        if (!config.has("kafka")) {
            throw new ConfigLoadException("Missing 'kafka' object in config.json!");
        }
        return config.getJSONObject("kafka");
    }

    private static JSONObject getEsObject() {
        if (!config.has("elasticsearch")) {
            throw new ConfigLoadException("Missing 'elasticsearch' object in config.json!");
        }
        return config.getJSONObject("elasticsearch");
    }

    // ========== Kafka ==========

    public static String getKafkaBootstrapServers() {
        return getKafkaObject().getString("bootstrapServers");
    }

    public static String getKafkaGroupId() {
        return getKafkaObject().getString("groupId");
    }

    public static String getKafkaTopic() {
        return getKafkaObject().getString("topic");
    }

    // ========== Elasticsearch ==========

    public static String getEsHost() {
        return getEsObject().getString("host");
    }

    public static int getEsPort() {
        return getEsObject().getInt("port");
    }

    public static String getEsIndexName() {
        return getEsObject().getString("indexName");
    }

}
