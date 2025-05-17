package com.mydomain.consumer_elasticsearch.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydomain.consumer_elasticsearch.exception.ConfigLoadException;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.InputStream;

/**
 * config.json içeriğini bir kez belleğe alır,
 * sonra statik getter’larla erişim sağlar.
 */
@Log4j2
public final class ConfigReader {

    private static final String CONFIG_FILE = "/config.json";
    private static final JsonNode root;

    /* Utility sınıf – nesne yaratılmasın */
    private ConfigReader() {
    }

    /* static blok: uygulama ayağa kalkarken tek seferde okur */
    static {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream is = ConfigReader.class.getResourceAsStream(CONFIG_FILE)) {
            if (is == null) {
                throw new ConfigLoadException("config.json not found on classpath!", null);
            }
            root = mapper.readTree(is);
            log.info("config.json loaded successfully.");
        } catch (IOException e) {
            throw new ConfigLoadException("Failed to parse config.json!", e);
        }
    }

    /* ---------- Kafka ---------- */
    public static String getKafkaBootstrapServers() {
        return root.at("/kafka/bootstrapServers").asText();
    }

    public static String getKafkaTopic() {
        return root.at("/kafka/topic").asText();
    }

    public static String getKafkaGroupId() {
        return root.at("/kafka/groupId").asText();
    }

    public static String getAutoOffsetReset() {
        return root.at("/kafka/autoOffsetReset").asText("latest");
    }

    /* ---------- Elasticsearch ---------- */
    public static String getEsHost() {
        return root.at("/elasticsearch/host").asText();
    }

    public static int getEsPort() {
        return root.at("/elasticsearch/port").asInt(9200);
    }

    public static String getEsScheme() {
        return root.at("/elasticsearch/scheme").asText("http");
    }

    public static String getEsIndex() {
        return root.at("/elasticsearch/index").asText("rates_index");
    }

}
