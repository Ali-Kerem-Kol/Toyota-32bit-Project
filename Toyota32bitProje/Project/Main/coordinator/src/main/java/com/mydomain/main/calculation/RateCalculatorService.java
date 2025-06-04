package com.mydomain.main.calculation;

import com.mydomain.main.exception.CalculationException;
import com.mydomain.main.exception.FormulaEngineException;
import com.mydomain.main.model.Rate;
import com.mydomain.main.model.RateFields;
import com.mydomain.main.model.RateStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * Platformlardan gelen verilerle hesaplama yapar.
 * Redis gibi dış servis bağımlılığı yoktur.
 * •   USDTRY zorunludur.
 * •   Çapraz kurlar için (örneğin EURUSD) ek veri gerekir.
 */
public class RateCalculatorService {

    private static final Logger logger = LogManager.getLogger(RateCalculatorService.class);
    private final Set<String> shortnames;

    public RateCalculatorService(Set<String> shortnames) {
        this.shortnames = shortnames;
    }

    /**
     * Verilen grouped veriler üzerinden tüm kısa adlar için hesaplama yapar.
     *
     * @param groupedRates shortName → List<Rate> eşleşmeleri
     * @return resultName → Rate eşleşmeleri
     * @throws FormulaEngineException Eğer formül motorunda kritik bir hata oluşursa
     */
    public Map<String, Rate> calculate(Map<String, List<Rate>> groupedRates) throws FormulaEngineException {
        if (groupedRates == null || groupedRates.isEmpty()) {
            logger.warn("Grouped rate list is empty — skipping calculation.");
            return Collections.emptyMap();
        }

        if (!groupedRates.containsKey("USDTRY") || groupedRates.get("USDTRY").isEmpty()) {
            logger.warn("No USDTRY data available — calculation aborted.");
            return Collections.emptyMap();
        }

        Map<String, Rate> calculatedRates = new HashMap<>();

        for (String shortName : shortnames) {
            if (!groupedRates.containsKey(shortName) && !"USDTRY".equals(shortName)) {
                logger.warn("No data for shortName='{}' — skipping.", shortName);
                continue;
            }

            try {
                logger.trace("Computing rate for '{}'", shortName);
                Rate calc = compute(shortName, groupedRates);
                calculatedRates.put(calc.getRateName(), calc);

                logger.info("✅ Calculated {} → bid={}, ask={}",
                        calc.getRateName(),
                        calc.getFields().getBid(),
                        calc.getFields().getAsk());

            } catch (FormulaEngineException e) {
                throw e; // Kritik hata üst katmana fırlatılır
            } catch (CalculationException e) {
                logger.error("❌ CalculationException for '{}': {}", shortName, e.getMessage());
            } catch (Exception e) {
                logger.error("❌ Unexpected error while calculating '{}': {}", shortName, e.getMessage(), e);
            }
        }

        return calculatedRates;
    }

    /**
     * Belirli bir kısa ad (örneğin USDTRY, EURUSD) için hesaplama yapar.
     *
     * @param shortName     Hesaplanacak kısa ad
     * @param groupedRates  Girdi veri seti
     * @return Oluşturulan Rate nesnesi
     * @throws FormulaEngineException JavaScript motoru veya formül kaynaklı kritik hata durumunda
     */
    private Rate compute(String shortName, Map<String, List<Rate>> groupedRates) throws FormulaEngineException {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("calcName", shortName);

        // USDTRY verileri
        for (Rate r : groupedRates.getOrDefault("USDTRY", List.of())) {
            String pf = extractPlatformPrefix(r.getRateName());
            ctx.put(pf + "UsdtryBid", r.getFields().getBid());
            ctx.put(pf + "UsdtryAsk", r.getFields().getAsk());
        }

        // Çapraz kur verileri
        if (!"USDTRY".equals(shortName)) {
            String camel = shortName.substring(0, 1).toUpperCase() + shortName.substring(1).toLowerCase();
            for (Rate r : groupedRates.getOrDefault(shortName, List.of())) {
                String pf = extractPlatformPrefix(r.getRateName());
                ctx.put(pf + camel + "Bid", r.getFields().getBid());
                ctx.put(pf + camel + "Ask", r.getFields().getAsk());
            }
        }

        try {
            double[] result = DynamicFormulaService.calculate(ctx);
            String resultName = shortName.endsWith("USD") && !shortName.equals("USDTRY")
                    ? shortName.substring(0, 3) + "TRY"
                    : shortName;

            logger.trace("Calculated result for '{}': bid={}, ask={}", resultName, result[0], result[1]);

            return new Rate(
                    resultName,
                    new RateFields(result[0], result[1], System.currentTimeMillis()),
                    new RateStatus(true, true)
            );

        } catch (FormulaEngineException e) {
            throw e;
        } catch (Exception e) {
            throw new CalculationException("Formula script failed for shortName=" + shortName, e);
        }
    }

    private String extractPlatformPrefix(String rateName) {
        int index = rateName.indexOf('_');
        return (index > 0) ? rateName.substring(0, index).toLowerCase() : "unknown";
    }
}
