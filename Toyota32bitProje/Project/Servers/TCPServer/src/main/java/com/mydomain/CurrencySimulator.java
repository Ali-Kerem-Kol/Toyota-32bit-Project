package com.mydomain;

import java.util.Random;

/**
 * CurrencySimulator sınıfı, verilen başlangıç kuru üzerinden
 * rastgele dalgalanmalar simüle ederek yeni döviz kuru değeri üretir.
 */
public class CurrencySimulator {
    private static final Random random = new Random();

    /**
     * Başlangıç döviz kuru değerine -%1 ile +%1 arasında rastgele bir değişim uygulayarak
     * simüle edilmiş yeni döviz kuru değerini hesaplar.
     *
     * @param initialRate simülasyona başlanacak orijinal döviz kuru
     * @return değişim uygulanmış yeni döviz kuru değeri
     */
    public static double simulateExchangeRate(double initialRate) {
        double changePercent = (random.nextDouble() * 0.02) - 0.01; // -1% ile +1% arasında değişim
        return initialRate * (1 + changePercent);
    }
}
