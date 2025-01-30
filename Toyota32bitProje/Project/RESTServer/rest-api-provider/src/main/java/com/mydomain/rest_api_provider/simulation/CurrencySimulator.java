package com.mydomain.rest_api_provider.simulation;

import org.springframework.stereotype.Component;
import java.util.Random;

@Component
public class CurrencySimulator {
    private static final Random random = new Random();

    // Döviz çifti simülasyonu, aynı şekilde çalışacak
    public double simulateExchangeRate(double initialRate) {
        double changePercent = (random.nextDouble() * 0.02) - 0.01; // -1% ile +1% arasında değişim
        return initialRate * (1 + changePercent);
    }
}
