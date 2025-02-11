package com.mydomain.main.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydomain.main.coordinator.CoordinatorInterface;
import com.mydomain.main.http.HttpService;
import com.mydomain.main.model.Rate;
import com.mydomain.main.model.RateFields;
import com.mydomain.main.model.RateStatus;
import java.time.Instant;
import java.util.Map;

public class RESTProvider implements Provider {
    private final HttpService httpService;
    private final String apiUrl;
    private final ObjectMapper objectMapper;
    private CoordinatorInterface coordinator; // 🔹 Koordinatör referansı

    public RESTProvider(HttpService httpService, String apiUrl) {
        this.httpService = httpService;
        this.apiUrl = apiUrl;
        this.objectMapper = new ObjectMapper(); // <-- ekledik
    }


    public void setCoordinator(CoordinatorInterface coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public void connect(String platformName, String userId, String password) {
        System.out.println("✅ Connected to REST platform: " + platformName);
        coordinator.onConnect(platformName, true); // 🔹 Koordinatöre bağlantıyı bildir
    }

    @Override
    public void connect(String platformName, Map<String, String> params) {
        System.out.println("✅ Connected to REST platform: " + platformName + " with params: " + params);
        coordinator.onConnect(platformName, true);
    }

    @Override
    public void disconnect(String platformName) {
        System.out.println("✅ Disconnected from REST platform: " + platformName);
        coordinator.onDisConnect(platformName, true); // 🔹 Koordinatöre bağlantının kesildiğini bildir
    }

    @Override
    public void subscribe(String platformName, String rateName) {
        throw new UnsupportedOperationException("❌ REST API does not support subscription.");
    }

    @Override
    public void unsubscribe(String platformName, String rateName) {
        System.out.println("✅ Unsubscribed from " + rateName + " on " + platformName);
    }

    @Override
    public Rate fetchRate(String platformName, String rateName) {
        try {
            // 🔹 API'den JSON verisini çek
            String url = apiUrl + "/" + rateName;
            String jsonResponse = httpService.get(url);

            // 🔹 JSON'u işle
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            RateFields fields = new RateFields(
                    rootNode.get("bid").asDouble(),
                    rootNode.get("ask").asDouble(),
                    Instant.parse(rootNode.get("timestamp").asText()).toEpochMilli()
            );

            RateStatus status = new RateStatus(true, true);
            Rate rate = new Rate(rateName, fields, status);

            // 🔹 İlk kez gelen oran için "onRateAvailable" çağrısı yap
            coordinator.onRateAvailable(platformName, rateName, rate);

            // 🔹 Güncellenmiş oran için "onRateUpdate" çağrısı yap
            coordinator.onRateUpdate(platformName, rateName, fields);

            // ✅ Konsola daha güzel yazdırma
            System.out.println("\n📝 REST API’den Gelen JSON (Pretty-Print Formatında):\n" + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode));

            return rate;
        } catch (Exception e) {
            // Tek satırlık log + throw new RuntimeException
            System.err.println("❌ Error fetching from REST: " + e.getMessage());
            throw new RuntimeException("❌ Failed to fetch rate from REST API: " + e.getMessage(), e);
        }
    }

}
