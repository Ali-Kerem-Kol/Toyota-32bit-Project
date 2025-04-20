package com.mydomain.rest_api_provider.controller;

import com.mydomain.rest_api_provider.config.ConfigReader;
import com.mydomain.rest_api_provider.simulation.CurrencySimulator;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
    public Map<String, Object> getExchangeRate(
            @PathVariable String rateName,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        // API Key doğrulaması
        String apiKey = configReader.getApiKey();
        if (apiKey != null && (authHeader == null || !authHeader.equals("Bearer " + apiKey))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized request");
        }

        double initialRate = configReader.getInitialRate(rateName);

        if (initialRate == -1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rate data not found for " + rateName);
        }

        double bid = currencySimulator.simulateExchangeRate(initialRate);
        //double ask = bid + 0.99;
        double ask = bid + 0.01;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("rateName", rateName);
        response.put("bid", bid);
        response.put("ask", ask);
        response.put("timestamp", Instant.now().toString());

        return response;
    }
}