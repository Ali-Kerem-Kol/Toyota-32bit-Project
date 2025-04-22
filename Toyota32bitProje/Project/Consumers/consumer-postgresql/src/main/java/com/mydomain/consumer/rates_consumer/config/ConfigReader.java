package com.mydomain.consumer.rates_consumer.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;

/**
 * config.json dosyasını okuyarak tüketici uygulaması için
 * gerekli ayarları sağlayan yardımcı sınıf.
 * Yüklenen ayarlar statik blokta okunur ve JsonNode içinde saklanır.
 */
public class ConfigReader {

    private static final Logger logger = LogManager.getLogger(ConfigReader.class);

    /** config.json içeriğini tutan JsonNode nesnesi */
    private static JsonNode config;

    static {
        try {
            InputStream is = ConfigReader.class.getClassLoader().getResourceAsStream("config.json");
            if (is == null) {
                throw new RuntimeException("config.json not found in resources!");
            }

            ObjectMapper mapper = new ObjectMapper();
            config = mapper.readTree(is);

            logger.info("Consumer config loaded successfully.");
        } catch (Exception e) {
            logger.error("Failed to load config.json: {}", e.getMessage(), e);
            throw new RuntimeException("Could not load config.json", e);
        }
    }

    /**
     * Kafka broker adreslerini döner.
     *
     * @return bootstrapServers - Kafka bootstrap sunucuları
     */
    public static String getKafkaBootstrapServers() {
        return config.get("bootstrapServers").asText();
    }

    /**
     * Dinlenecek Kafka topic adını döner.
     *
     * @return topicName - Kafka topic adı
     */
    public static String getTopicName() {
        return config.get("topicName").asText();
    }

    /**
     * Consumer grup kimliğini döner.
     *
     * @return groupId - Kafka consumer grup kimliği
     */
    public static String getGroupId() {
        return config.get("groupId").asText();
    }

    /**
     * Offset sıfırlama stratejisini döner.
     * "latest" veya "earliest" gibi değerler içerir.
     *
     * @return autoOffsetReset - offset reset stratejisi
     */
    public static String getAutoOffsetReset() {
        return config.get("autoOffsetReset").asText();
    }
}
