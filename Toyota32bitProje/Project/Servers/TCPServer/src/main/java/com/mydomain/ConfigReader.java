package com.mydomain;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONObject;

/**
 * ConfigReader sınıfı, verilen JSON yapılandırma dosyasını okuyup
 * içindeki ayarları elde etmeye yarar.
 */
public class ConfigReader {
    private JSONObject config;

    /**
     * Yapılandırma dosyasını yükler ve içeriğini parse eder.
     *
     * @param configFile JSON formatındaki yapılandırma dosyasının yolu
     */
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

    /**
     * Başlangıç döviz kuru değerini döner.
     *
     * @param rateName "initialRates" nesnesi içindeki döviz kuru anahtarı
     * @return ilgili döviz kuru değeri
     * @throws org.json.JSONException anahtar bulunamazsa veya tip uyumsuzluğu varsa
     */
    public double getInitialRate(String rateName) {
        return config.getJSONObject("initialRates").getDouble(rateName);
    }

    /**
     * Döviz çiftlerini döner.
     *
     * @return Döviz çiftlerini içeren bir Set
     * @throws org.json.JSONException ilgili anahtar yoksa veya tip uyumsuzluğu varsa
     */
    public Set<String> getInitialRates() {
        Set<String> currencyPairs = new HashSet<>();
        config.getJSONObject("initialRates").keys().forEachRemaining(currencyPairs::add);
        return currencyPairs;
    }

    /**
     * Veri yayını arasındaki bekleme süresini (milisaniye) döner.
     *
     * @return yayın sıklığı (milisaniye cinsinden)
     * @throws org.json.JSONException ilgili anahtar yoksa veya tip uyumsuzluğu varsa
     */
    public int getPublishFrequency() {
        return config.getInt("publishFrequency");
    }

    /**
     * Toplam kaç kez veri yayınlanacağını döner.
     *
     * @return yayın sayısı
     * @throws org.json.JSONException ilgili anahtar yoksa veya tip uyumsuzluğu varsa
     */
    public int getPublishCount() {
        return config.getInt("publishCount");
    }
}
