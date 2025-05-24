package com.mydomain.main.service;

import com.mydomain.main.config.ConfigReader;
import com.mydomain.main.model.Rate;
import com.mydomain.main.model.RateFields;
import com.mydomain.main.model.RateStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.*;

/**
 * Dışarıdan verilen verilerle hesaplama yapar. Redis bağımlılığı yoktur.
 * •   USDTRY ya da çaprazlar için en az **bir** platform yeterlidir.
 * •   Eksik platformlar yalnızca WARN loglanır; hesaplama devam eder.
 */
public class RateCalculatorService {

    private static final Logger logger = LogManager.getLogger(RateCalculatorService.class);

    private final Set<String> shortnames;                    // USDTRY, EURUSD, ...

    public RateCalculatorService() {
        this.shortnames  = ConfigReader.getSubscribeRatesShort();
    }

    /**
     * Verilen grouped veriler üzerinden tüm kısa adlar (shortName) için hesaplama yapar.
     * @param groupedRates shortName → List<Rate> şeklinde gruplandırılmış ham veriler
     * @return Hesaplanmış kurlar: resultName → Rate
     */
    public Map<String, Rate> calculate(Map<String, List<Rate>> groupedRates) {
        if (!groupedRates.containsKey("USDTRY") || groupedRates.get("USDTRY").isEmpty() || groupedRates.isEmpty()) {
            logger.warn("❌ Hiç USDTRY verisi yok; hesaplama atlandı.");
            return Collections.emptyMap();
        }

        Map<String, Rate> calculatedRates = new HashMap<>();

        for (String shortName : shortnames) {
            if (!groupedRates.containsKey(shortName) && !shortName.equals("USDTRY")) {
                logger.warn("💡 {} verisi yok; atlanıyor.", shortName);
                continue;
            }

            try {
                Rate calc = compute(shortName, groupedRates);
                calculatedRates.put(calc.getRateName(), calc);

                logger.info("🔹 {} => bid={}, ask={}",
                        calc.getRateName(),
                        calc.getFields().getBid(),
                        calc.getFields().getAsk());
            } catch (Exception e) {
                logger.error("❌ {} hesaplanırken hata: {}", shortName, e.getMessage(), e);
            }
        }

        return calculatedRates;
    }

    /**
     * Belirli bir kısa ad (USDTRY, EURUSD vb.) için hesaplama yapar.
     * @param shortName USDTRY, EURUSD gibi kısa ad
     * @param groupedRates Veriler
     * @return Hesaplanmış Rate
     */
    private Rate compute(String shortName, Map<String, List<Rate>> groupedRates) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("calcName", shortName);

        // USDTRY her zaman gerekir
        List<Rate> usdtryRates = groupedRates.getOrDefault("USDTRY", List.of());
        for (Rate r : usdtryRates) {
            String pf = r.getRateName().substring(0, r.getRateName().indexOf('_')).toLowerCase(); // pf1, pf2, ...
            ctx.put(pf + "UsdtryBid", r.getFields().getBid());
            ctx.put(pf + "UsdtryAsk", r.getFields().getAsk());
        }

        // Eğer çaprazsa (örneğin EURUSD), onu da ekle
        if (!"USDTRY".equals(shortName)) {
            String camel = shortName.substring(0, 1).toUpperCase() + shortName.substring(1).toLowerCase(); // EURUSD → Eurusd
            List<Rate> crossRates = groupedRates.getOrDefault(shortName, List.of());
            for (Rate r : crossRates) {
                String pf = r.getRateName().substring(0, r.getRateName().indexOf('_')).toLowerCase();
                ctx.put(pf + camel + "Bid", r.getFields().getBid());
                ctx.put(pf + camel + "Ask", r.getFields().getAsk());
            }
        }

        // JavaScript ile hesapla
        double[] result = DynamicFormulaService.calculate(ctx);

        // Örn: EURUSD → EURTRY
        String resultName = shortName.endsWith("USD") && !shortName.equals("USDTRY")
                ? shortName.substring(0, 3) + "TRY"
                : shortName;

        return new Rate(
                resultName,
                new RateFields(result[0], result[1], System.currentTimeMillis()),
                new RateStatus(true, true)
        );
    }
}
