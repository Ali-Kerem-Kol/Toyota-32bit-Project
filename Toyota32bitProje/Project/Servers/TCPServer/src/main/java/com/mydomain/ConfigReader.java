package com.mydomain;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.File;
import org.json.JSONObject;

public class ConfigReader {
    private JSONObject config;

    public ConfigReader(String configFile) {
        try {
            // Dosya yolunu mutlak yol olarak belirleyelim
            File file = new File(configFile);
            if (!file.exists()) {
                System.err.println("Config file not found: " + configFile);
                return;
            }

            // Dosyayı okuma
            String content = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())));
            //String content = new String(Files.readAllBytes(Paths.get("src/main/java/resources/config.json")));
            config = new JSONObject(content);
        } catch (Exception e) {
            System.err.println("Error loading configuration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public double getInitialRate(String rateName) {
        return config.getJSONObject("initialRates").getDouble(rateName);
    }

    public int getPublishFrequency() {
        return config.getInt("publishFrequency");
    }

    public int getPublishCount() {
        return config.getInt("publishCount");
    }
}
