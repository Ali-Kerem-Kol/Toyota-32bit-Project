package com.mydomain.rest_api_provider.controller;

import com.mydomain.rest_api_provider.config.ConfigReader;
import com.mydomain.rest_api_provider.simulation.CurrencySimulator;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Döviz kuru isteklerini karşılayan REST kontrolcüsü.
 * <p>
 * /api/rates/{rateName} endpoint'ine gelen GET isteklerini işleyerek
 * yapılandırmadan alınan başlangıç kurunu simüle eder ve JSON formatında döner.
 * </p>
 */
@RestController
@RequestMapping("/api/rates")
public class ExchangeRateController {

    private final ConfigReader configReader;
    private final CurrencySimulator currencySimulator;

    /**
     * ExchangeRateController yapıcısı.
     *
     * @param configReader      Yapılandırma bilgilerini sağlayan ConfigReader bileşeni
     * @param currencySimulator Döviz kuru simülasyonu yapan CurrencySimulator bileşeni
     */
    public ExchangeRateController(ConfigReader configReader, CurrencySimulator currencySimulator) {
        this.configReader = configReader;
        this.currencySimulator = currencySimulator;
    }

    /**
     * Belirtilen döviz kuru adı için güncel bid/ask değerlerini dönen endpoint.
     * <p>
     * İstekte geçerli bir API anahtarı (Bearer token) bulunmuyorsa 401 döner.
     * Eğer istenen rateName yapılandırmada bulunamazsa 404 döner.
     * </p>
     *
     * @param rateName   Path variable olarak gelen döviz kuru adı (ör. "PF2_USDTRY")
     * @param authHeader Authorization başlığındaki Bearer token (isteğe bağlı)
     * @return rateName, bid, ask ve timestamp içeren JSON haritası
     * @throws ResponseStatusException Yetkisiz istek veya bulunamayan veri durumunda fırlatılır
     */
    @GetMapping("/{rateName}")
    public Map<String, Object> getExchangeRate(
            @PathVariable String rateName,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        // API Key doğrulama
        String apiKey = configReader.getApiKey();
        if (apiKey != null && (authHeader == null || !authHeader.equals("Bearer " + apiKey))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized request");
        }

        // Başlangıç kuru al
        double initialRate = configReader.getInitialRate(rateName);

        // Bulunamazsa 404
        if (initialRate == -1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rate data not found for " + rateName);
        }

        // Simülasyonla bid ve ask hesapla
        double bid = currencySimulator.simulateExchangeRate(initialRate);
        double ask = bid + 0.01;

        // Yanıt haritası oluştur
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("rateName", rateName);
        response.put("bid", bid);
        response.put("ask", ask);
        response.put("timestamp", Instant.now().toString());

        return response;
    }
}
