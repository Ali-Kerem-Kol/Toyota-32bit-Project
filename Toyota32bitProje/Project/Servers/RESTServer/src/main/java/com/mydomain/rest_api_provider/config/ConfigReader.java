package com.mydomain.rest_api_provider.config;

import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Uygulama yapılandırmasını (config.json) okuyan ve sunan sınıf.
 * Başlangıç döviz kurları, API anahtarı ve yayın ayarları gibi yapılandırma bilgilerini sağlar.
 */
@Component
public class ConfigReader {
    private JSONObject config;

    private static final String CONFIG_FILE_PATH = "/app/Servers/RESTServer/config/config.json";

    /**
     * Yapılandırma dosyasını okuyup JSON nesnesi olarak yükler.
     * Docker ortamında: /app/rest-server/config/config.json yolundan okur.
     */
    public ConfigReader() {
        try {
            String content = new String(Files.readAllBytes(Paths.get(CONFIG_FILE_PATH)));
            config = new JSONObject(content);
        } catch (Exception e) {
            throw new RuntimeException("Yapılandırma yüklenirken hata oluştu: " + e.getMessage(), e);
        }
    }

    public double getInitialRate(String rateName) {
        return config.getJSONObject("initialRates").optDouble(rateName, -1);
    }

    public String getApiKey() {
        return config.optString("apiKey", null);
    }

}