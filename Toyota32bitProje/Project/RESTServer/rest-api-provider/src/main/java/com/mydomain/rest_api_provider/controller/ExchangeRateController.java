package com.mydomain.rest_api_provider.controller;

import com.mydomain.rest_api_provider.config.ConfigReader;
import com.mydomain.rest_api_provider.simulation.CurrencySimulator;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/rates")
public class ExchangeRateController {

    private final ConfigReader configReader;
    private final CurrencySimulator currencySimulator;

    public ExchangeRateController(ConfigReader configReader, CurrencySimulator currencySimulator) {
        this.configReader = configReader;
        this.currencySimulator = currencySimulator;
    }

    @GetMapping("/{rateName}")
    public Map<String, Object> getExchangeRate(@PathVariable String rateName) {
        // LinkedHashMap kullanarak sıralamayı koruyoruz
        Map<String, Object> response = new LinkedHashMap<>();
        double initialRate = configReader.getInitialRate(rateName);

        if (initialRate == -1) {
            response.put("error", "Rate data not found for " + rateName);
        } else {
            double bid = currencySimulator.simulateExchangeRate(initialRate);
            double ask = bid + 0.99; // Spread ekleyerek ask fiyatını oluşturuyoruz

            // Bid önce, Ask sonra ekleniyor
            response.put("rateName", rateName);
            response.put("bid", bid);  // Bid önce ekleniyor
            response.put("ask", ask);  // Ask sonra ekleniyor
            response.put("timestamp", Instant.now().toString());
        }
        return response;
    }
}
