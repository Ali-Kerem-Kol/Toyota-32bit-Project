package com.mydomain.main.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.file.Paths;

public class ConfigReader {
    private static JsonNode config;

    static {
        try {
            // `config.json` dosyasını resources klasöründen oku
            ObjectMapper objectMapper = new ObjectMapper();
            InputStream inputStream = ConfigReader.class.getClassLoader().getResourceAsStream("config.json");
            if (inputStream == null) {
                throw new RuntimeException("❌ Configuration file (config.json) not found!");
            }
            config = objectMapper.readTree(inputStream);
        } catch (Exception e) {
            throw new RuntimeException("❌ Failed to load configuration file: " + e.getMessage());
        }
    }

    public static String getTcpHost() {
        return config.get("tcpServer").get("host").asText();
    }

    public static int getTcpPort() {
        return config.get("tcpServer").get("port").asInt();
    }

    public static String getRestApiUrl() {
        return config.get("restApi").get("url").asText();
    }

    public static String getRestApiKey() {
        return config.get("restApi").get("apiKey").asText();
    }
}
