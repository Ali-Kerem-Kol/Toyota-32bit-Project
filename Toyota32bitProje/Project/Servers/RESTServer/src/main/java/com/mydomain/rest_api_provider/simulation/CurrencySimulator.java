package com.mydomain.rest_api_provider.simulation;

import org.springframework.stereotype.Component;
import java.util.Random;

/**
 * Döviz kuru simülasyonu yapan bileşen.
 * <p>
 * Belirtilen başlangıç kuruna göre rastgele bir yüzde değişim (-1% ile +1% arasında)
 * uygulayarak yeni bir kur değeri hesaplar.
 * </p>
 */
@Component
public class CurrencySimulator {
    private static final Random random = new Random();

    /**
     * Verilen başlangıç kuruna göre simülasyon yapar.
     * <p>
     * Kur, -1% ile +1% arası rastgele bir oranda değiştirilir.
     * </p>
     *
     * @param initialRate Simülasyona temel olarak kullanılacak başlangıç kuru
     * @return Rastgele değişim uygulanmış yeni döviz kuru değeri
     */
    public double simulateExchangeRate(double initialRate) {
        double changePercent = (random.nextDouble() * 0.02) - 0.01; // -1% ile +1% arasında değişim
        return initialRate * (1 + changePercent);
    }
}
