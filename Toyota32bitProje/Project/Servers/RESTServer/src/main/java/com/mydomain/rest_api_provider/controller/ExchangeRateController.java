package com.mydomain.rest_api_provider.controller;

import com.mydomain.rest_api_provider.service.ExchangeRateService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rates")
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    public ExchangeRateController(ExchangeRateService exchangeRateService) {
        this.exchangeRateService = exchangeRateService;
    }

    @GetMapping("/{rateName}")
    public Map<String, Object> getExchangeRate(
            @PathVariable String rateName,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return exchangeRateService.getExchangeRate(rateName, authHeader);
    }
}
