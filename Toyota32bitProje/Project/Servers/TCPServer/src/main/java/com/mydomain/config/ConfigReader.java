package com.mydomain.config;

import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Yapılandırma dosyasını okuyan ve config parametrelerine güvenli erişim sağlayan sınıf.
 */
public class ConfigReader {

    private final JSONObject config;

    private static final String CONFIG_FILE_PATH = "/app/Servers/TCPServer/config/config.json";

    public ConfigReader() {
        try {
            File file = new File(CONFIG_FILE_PATH);
            if (!file.exists()) {
                throw new RuntimeException("❌ Config file not found: " + CONFIG_FILE_PATH);
            }

            String content = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())));
            this.config = new JSONObject(content);
        } catch (Exception e) {
            throw new RuntimeException("❌ Error loading configuration: " + e.getMessage(), e);
        }
    }

    /**
     * Belirtilen döviz kuru için yapılandırılmış başlangıç oranını verir.
     *
     * @param rateName Kur adı (ör: PF1_USDTRY)
     * @return Başlangıç oranı
     */
    public double getInitialRate(String rateName) {
        JSONObject initialRates = config.optJSONObject("initialRates");
        if (initialRates == null || !initialRates.has(rateName)) {
            throw new IllegalArgumentException("❌ No initial rate configured for: " + rateName);
        }
        return initialRates.getDouble(rateName);
    }

    /**
     * Tüm kur adlarını döner.
     */
    public Set<String> getInitialRates() {
        Set<String> currencyPairs = new HashSet<>();
        JSONObject initialRates = config.optJSONObject("initialRates");
        if (initialRates != null) {
            initialRates.keys().forEachRemaining(currencyPairs::add);
        }
        return currencyPairs;
    }

    /**
     * Yayın frekansını (ms) döner.
     */
    public int getPublishFrequency() {
        return config.optInt("publishFrequency", 1000); // default fallback
    }
}
