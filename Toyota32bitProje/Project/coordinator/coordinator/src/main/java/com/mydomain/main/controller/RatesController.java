package com.mydomain.main.controller;

import com.mydomain.main.coordinator.CoordinatorInterface;
import com.mydomain.main.model.Rate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/rates")
public class RatesController {

    private final CoordinatorInterface coordinator;

    @Autowired
    public RatesController(CoordinatorInterface coordinator) {
        this.coordinator = coordinator;
    }

    // Tüm oranları getir
    @GetMapping
    public Collection<Rate> getAllRates() {
        return coordinator.getAllRates();
    }

    // Belirli bir oranı getir
    @GetMapping("/{rateName}")
    public Rate getRateByName(@PathVariable String rateName) {
        return coordinator.getRate(rateName);
    }

    /**
     * Yeni eklediğimiz endpoint:
     * Postman'da: POST /api/rates/fetchFromRest?rateName=PF2_USDTRY
     * veya GET/PUT kullanabilirsin, proje ihtiyacına göre şekillendir.
     */
    @PostMapping("/fetchFromRest")
    public Rate fetchRateFromRest(@RequestParam String rateName) {
        // Koordinatör içindeki RESTProvider'a gidip, tek seferlik veri çek
        // Burada "REST_PROVIDER" senin coordinator içi platform adın.
        Rate fetchedRate = coordinator.getRestProvider().fetchRate("REST_PROVIDER", rateName);
        // Dokümanda "REST Provider subscription yok" diyordu, bu tamamen tek seferlik fetch.

        // Coordinator callback'leri (onRateAvailable/onRateUpdate) zaten tetikleniyor.
        // Geriye bu Rate objesini JSON olarak döndürebiliriz:
        return fetchedRate;
    }
}

