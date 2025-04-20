package com.mydomain;

import java.util.Random;

public class CurrencySimulator {
    private static final Random random = new Random();

    public static double simulateExchangeRate(double initialRate) {
        double changePercent = (random.nextDouble() * 0.02) - 0.01; // -1% ile +1% arasında değişim
        return initialRate * (1 + changePercent);
    }
}
