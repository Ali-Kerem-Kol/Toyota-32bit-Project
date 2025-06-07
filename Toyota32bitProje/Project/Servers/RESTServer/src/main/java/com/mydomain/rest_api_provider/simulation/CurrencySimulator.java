package com.mydomain.rest_api_provider.simulation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CurrencySimulator sınıfı, belirli bir başlangıç kuruna göre
 * dalgalanma ve nadiren spike etkisi ile simüle edilmiş kur üretir.
 */
@Component
public class CurrencySimulator {

    private static final Logger logger = LogManager.getLogger(CurrencySimulator.class);

    private static final double NORMAL_FLUCTUATION_PERCENT = 0.05; // ±%5
    private static final double SPIKE_PERCENT = 0.90;              // ±%90
    private static final double SPIKE_PROBABILITY = 0.20;          // %20 ihtimalle spike
    private static final int SPIKE_DELAY_COUNT = 5;                // İlk 5 istek spike yapmasın

    //private static final Random random = new Random();
    private final Random random = new Random(UUID.randomUUID().hashCode() + System.nanoTime());
    private final Map<String, Integer> requestCounts = new ConcurrentHashMap<>();

    /**
     * Başlangıç kuruna göre yeni bir bid üretir.
     *
     * @param rateName    Döviz kuru adı (ör: PF2_USDTRY)
     * @param initialRate Başlangıç kuru
     * @return Simüle edilmiş yeni bid değeri
     */
    public double simulateExchangeRate(String rateName, double initialRate) {
        int count = requestCounts.getOrDefault(rateName, 0);
        requestCounts.put(rateName, count + 1);

        double changePercent;

        if (count < SPIKE_DELAY_COUNT) {
            changePercent = getNormalFluctuation();
        } else if (random.nextDouble() < SPIKE_PROBABILITY) {
            changePercent = getSpikeFluctuation();
            logger.info("💥 Spike generated for {} → {}%", rateName, (int) (changePercent * 100));
        } else {
            changePercent = getNormalFluctuation();
        }

        double newRate = Math.max(0.01, initialRate * (1 + changePercent));
        logger.debug("Simulated rate for {}: initial={} → new={}", rateName, initialRate, newRate);
        return newRate;
    }

    private double getNormalFluctuation() {
        return (random.nextDouble() * 2 * NORMAL_FLUCTUATION_PERCENT) - NORMAL_FLUCTUATION_PERCENT;
    }

    private double getSpikeFluctuation() {
        return random.nextBoolean() ? SPIKE_PERCENT : -SPIKE_PERCENT;
    }
}
