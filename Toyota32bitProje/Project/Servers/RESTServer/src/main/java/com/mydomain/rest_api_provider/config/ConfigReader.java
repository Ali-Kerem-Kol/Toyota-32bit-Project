package com.mydomain.rest_api_provider.config;

import org.json.JSONObject;
import org.springframework.stereotype.Component;
import java.nio.file.Files;
import java.nio.file.Paths;

@Component
public class ConfigReader {
    private JSONObject config;

    public ConfigReader() {
        try {
            String content = new String(Files.readAllBytes(Paths.get("src/main/resources/config.json")));
            config = new JSONObject(content);
        } catch (Exception e) {
            throw new RuntimeException("Error loading configuration: " + e.getMessage(), e);
        }
    }

    // Başlangıç kur değerini döndürür
    public double getInitialRate(String rateName) {
        return config.getJSONObject("initialRates").optDouble(rateName, -1);
    }

    // API key varsa döndürür, yoksa null döner
    public String getApiKey() {
        return config.optString("apiKey", null);
    }

    public int getPublishFrequency() {
        return config.getInt("publishFrequency");
    }

    public int getPublishCount() {
        return config.getInt("publishCount");
    }
}