package com.mydomain.consumer.rates_consumer.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;

public class ConfigReader {

    private static final Logger logger = LogManager.getLogger(ConfigReader.class);

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

    public static String getKafkaBootstrapServers() {
        return config.get("bootstrapServers").asText();
    }

    public static String getTopicName() {
        return config.get("topicName").asText();
    }

    public static String getGroupId() {
        return config.get("groupId").asText();
    }

    public static String getAutoOffsetReset() {
        return config.get("autoOffsetReset").asText();
    }
}
