package com.mydomain.simulation;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CurrencySimulator, sadece gerçekten gönderilen kurların sayısını takip eder.
 * İlk 5 gönderide spike yapmaz. Sonrasında spike ihtimali devreye girer.
 */
public class CurrencySimulator {

    private static final double NORMAL_FLUCTUATION_PERCENT = 0.05; // ±%5
    private static final double SPIKE_PERCENT = 0.90;              // ±%90
    private static final double SPIKE_PROBABILITY = 0.20;          // %20 ihtimalle spike
    private static final int SPIKE_DELAY_COUNT = 5;                // İlk 5 gönderide spike yapma

    //private static final Random random = new Random();
    private final Random random = new Random(UUID.randomUUID().hashCode() + System.nanoTime());

    // Gerçekten gönderilmiş rate sayısı
    private final Map<String, Integer> publishCounts = new ConcurrentHashMap<>();

    /**
     * Simüle edilmiş bid değeri üretir.
     * @param rateName     Kur adı
     * @param initialRate  Başlangıç değeri
     * @param markAsSent   Gerçekten gönderildiyse true (sayacı artırmak için)
     * @return Yeni bid fiyatı
     */
    public double simulateExchangeRate(String rateName, double initialRate, boolean markAsSent) {
        int count = publishCounts.getOrDefault(rateName, 0);

        double changePercent;
        if (count < SPIKE_DELAY_COUNT) {
            changePercent = getNormalFluctuation();
        } else if (random.nextDouble() < SPIKE_PROBABILITY) {
            changePercent = getSpikeFluctuation();
        } else {
            changePercent = getNormalFluctuation();
        }

        if (markAsSent) {
            publishCounts.put(rateName, count + 1);
        }

        return Math.max(0.01, initialRate * (1 + changePercent));
    }

    private double getNormalFluctuation() {
        return (random.nextDouble() * 2 * NORMAL_FLUCTUATION_PERCENT) - NORMAL_FLUCTUATION_PERCENT;
    }

    private double getSpikeFluctuation() {
        return random.nextBoolean() ? SPIKE_PERCENT : -SPIKE_PERCENT;
    }
}
