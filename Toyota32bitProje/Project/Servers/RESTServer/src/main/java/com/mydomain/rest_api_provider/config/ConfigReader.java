package com.mydomain.rest_api_provider.config;

import org.json.JSONObject;
import org.springframework.stereotype.Component;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Uygulama yapılandırmasını (config.json) okuyan ve sunan sınıf.
 * <p>
 * Başlangıç döviz kurları, API anahtarı ve yayın ayarları gibi
 * yapılandırma bilgilerini sağlar.
 * </p>
 */
@Component
public class ConfigReader {
    private JSONObject config;

    /**
     * Yapılandırma dosyasını okuyup JSON nesnesi olarak yükler.
     * Dosya yükleme sırasında hata oluşursa RuntimeException fırlatır.
     */
    public ConfigReader() {
        try {
            String content = new String(Files.readAllBytes(Paths.get("src/main/resources/config.json")));
            config = new JSONObject(content);
        } catch (Exception e) {
            throw new RuntimeException("Yapılandırma yüklenirken hata oluştu: " + e.getMessage(), e);
        }
    }

    /**
     * Konfigürasyonda tanımlı başlangıç kurlarından verilen anahtarı döndürür.
     *
     * @param rateName İstenen döviz kuru anahtarı (örneğin "PF2_USDTRY")
     * @return Belirtilen kur değeri; bulunamazsa -1
     */
    public double getInitialRate(String rateName) {
        return config.getJSONObject("initialRates").optDouble(rateName, -1);
    }

    /**
     * Konfigürasyonda tanımlı API anahtarını döndürür.
     *
     * @return API anahtarı (Bearer token); yoksa null
     */
    public String getApiKey() {
        return config.optString("apiKey", null);
    }

    /**
     * Konfigürasyonda tanımlı veri yayın frekansını (ms cinsinden) döndürür.
     *
     * @return Yayın frekansı (milisaniye)
     */
    public int getPublishFrequency() {
        return config.getInt("publishFrequency");
    }

    /**
     * Konfigürasyonda tanımlı yayın sayısını döndürür.
     *
     * @return Yayın sayısı
     */
    public int getPublishCount() {
        return config.getInt("publishCount");
    }
}
