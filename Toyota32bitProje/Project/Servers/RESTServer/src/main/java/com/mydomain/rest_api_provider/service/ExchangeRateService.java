package com.mydomain.rest_api_provider.service;

import com.mydomain.rest_api_provider.config.ConfigReader;
import com.mydomain.rest_api_provider.simulation.CurrencySimulator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Döviz kuru iş mantığını yöneten servis sınıfı.
 */
@Service
public class ExchangeRateService {

    private final ConfigReader configReader;
    private final CurrencySimulator currencySimulator;

    public ExchangeRateService(ConfigReader configReader, CurrencySimulator currencySimulator) {
        this.configReader = configReader;
        this.currencySimulator = currencySimulator;
    }

    /**
     * Belirtilen döviz kuru için yetkilendirme kontrolü yapar ve simüle edilmiş veriyi döner.
     *
     * @param rateName   Kur adı (örneğin: PF2_USDTRY)
     * @param authHeader Bearer API Key header değeri
     * @return JSON olarak yanıt haritası
     */
    public Map<String, Object> getExchangeRate(String rateName, String authHeader) {
        // 1) API Key kontrolü
        String apiKey = configReader.getApiKey();
        if (apiKey != null && (authHeader == null || !authHeader.equals("Bearer " + apiKey))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized request");
        }

        // 2) Başlangıç kuru
        double initialRate = configReader.getInitialRate(rateName);
        if (initialRate == -1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rate not found: " + rateName);
        }

        // 3) Simülasyon
        double bid = currencySimulator.simulateExchangeRate(rateName, initialRate);
        double ask = bid + 0.01;

        // 4) JSON cevabı
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("rateName", rateName);
        response.put("bid", bid);
        response.put("ask", ask);
        response.put("timestamp", Instant.now().toString());

        return response;
    }
}
