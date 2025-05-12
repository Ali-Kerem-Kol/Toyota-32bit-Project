package com.mydomain.main.service;

import com.mydomain.main.config.ConfigReader;
import com.mydomain.main.model.Rate;
import com.mydomain.main.model.RateFields;
import com.mydomain.main.model.RateStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Kur hesaplamalarını yapan servis
 */
public class RateCalculatorService {

    private static final Logger logger = LogManager.getLogger(RateCalculatorService.class);
    private final RedisService redisService;

    public RateCalculatorService(RedisService redisService) {
        this.redisService = redisService;
    }


    /**
     * Ham kurları Redis’ten alır, Null ve pasif olanları filtreler,
     * JS formüllerini çalıştırır, sonucu Redis’e yazar ve döner.
     */
    public Map<String, Rate> calculateRates() {
        Map<String, Rate> calculated = new HashMap<>();

        // 1) Tüm tam ve kısa isimleri al
        Set<String> fullNames  = ConfigReader.getSubscribeRates();
        Set<String> shortNames = ConfigReader.getSubscribeRatesShort();

        // 2) Tam adları kısa ada göre grupla
        Map<String, List<String>> byShort = fullNames.stream()
                .collect(Collectors.groupingBy(fn -> fn.substring(fn.indexOf('_') + 1)));

        // 3) USDTRY grubunu al, Null/pasif olanları çıkar
        List<String> usdGroup = byShort.getOrDefault("USDTRY", Collections.emptyList());
        Map<String, Rate> rawUsd = usdGroup.stream()
                .map(fn -> new AbstractMap.SimpleEntry<>(fn, redisService.getRawRate(fn)))
                .filter(e -> e.getValue() != null
                        && e.getValue().getStatus().isActive()
                        && e.getValue().getStatus().isUpdated())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (rawUsd.size() != usdGroup.size()) {
            logger.warn("💡 USDTRY grubu eksik veya güncel değil, hesaplama atlandı.");
            return Collections.emptyMap();
        }

        // 4) Her kısa ada göre hesaplama döngüsü
        for (String shortName : shortNames) {
            try {
                boolean isCross = shortName.endsWith("USD") && !shortName.equals("USDTRY");
                String resultName = isCross
                        ? shortName.substring(0,3) + "TRY"
                        : shortName;

                List<String> groupFulls = byShort.getOrDefault(shortName, Collections.emptyList());
                // rawGroup’u Null/pasif öğeleri çıkararak oluştur
                Map<String, Rate> rawGroup = groupFulls.stream()
                        .map(fn -> new AbstractMap.SimpleEntry<>(fn, redisService.getRawRate(fn)))
                        .filter(e -> e.getValue() != null
                                && e.getValue().getStatus().isActive()
                                && e.getValue().getStatus().isUpdated())
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                if (rawGroup.size() != groupFulls.size()) {
                    logger.warn("💡 {} grubu eksik veya güncel değil, atlanıyor.", shortName);
                    continue;
                }

                // 5) JS formülü için context hazırla
                Map<String, Object> ctx = new HashMap<>();
                ctx.put("calcName", shortName);
                // USDTRY verileri
                rawUsd.forEach((full, rate) -> {
                    String provider = full.substring(0, full.indexOf('_')).toLowerCase();
                    ctx.put(provider + "UsdtryBid", rate.getFields().getBid());
                    ctx.put(provider + "UsdtryAsk", rate.getFields().getAsk());
                });
                // Cross‐rate verileri
                rawGroup.forEach((full, rate) -> {
                    String provider = full.substring(0, full.indexOf('_')).toLowerCase();
                    String currency = shortName.substring(0,1).toUpperCase()
                            + shortName.substring(1).toLowerCase();
                    ctx.put(provider + currency + "Bid", rate.getFields().getBid());
                    ctx.put(provider + currency + "Ask", rate.getFields().getAsk());
                });

                // 6) Hesapla
                double[] result = DynamicFormulaService.calculate(ctx);

                // 7) Sonucu kaydet ve döndür
                Rate calc = new Rate(
                        resultName,
                        new RateFields(result[0], result[1], System.currentTimeMillis()),
                        new RateStatus(true, true)
                );
                redisService.putCalculatedRate(resultName, calc);
                calculated.put(resultName, calc);
                logger.info("🔹 {} => bid={}, ask={}", resultName, result[0], result[1]);

            } catch (Exception e) {
                logger.error("❌ {} hesaplanırken hata: {}", shortName, e.getMessage(), e);
            }
        }

        return calculated;
    }
}
