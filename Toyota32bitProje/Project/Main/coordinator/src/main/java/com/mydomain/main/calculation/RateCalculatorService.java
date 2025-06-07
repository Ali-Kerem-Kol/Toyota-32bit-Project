package com.mydomain.main.calculation;

import com.mydomain.main.exception.CalculationException;
import com.mydomain.main.exception.FormulaEngineException;
import com.mydomain.main.model.Rate;
import com.mydomain.main.model.RateFields;
import com.mydomain.main.model.RateStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class RateCalculatorService {
    private static final Logger log = LogManager.getLogger(RateCalculatorService.class);
    private final Set<String> rateNames;

    public RateCalculatorService(Set<String> rateNames) {
        this.rateNames = new HashSet<>(rateNames);
    }

    public List<Rate> calculate(Map<String, Map<String, Rate>> groupedRates) {
        if (groupedRates == null || groupedRates.isEmpty()) {
            log.debug("Grouped rates is empty — skipping calculation.");
            return Collections.emptyList();
        }

        // Platform bazlı kurları rateName'e göre grupla
        Map<String, List<Rate>> ratesByRateName = groupRatesByName(groupedRates);

        // USDTRY kontrolü
        if (!ratesByRateName.containsKey("USDTRY") || ratesByRateName.get("USDTRY").isEmpty()) {
            log.debug("No USDTRY data available — calculation aborted.");
            return Collections.emptyList();
        }

        List<Rate> calculatedRates = new ArrayList<>();
        for (String rateName : rateNames) {
            if (!ratesByRateName.containsKey(rateName) && !"USDTRY".equals(rateName)) {
                log.debug("No data for rateName='{}' — skipping.", rateName);
                continue;
            }
            try {
                Rate calculatedRate = computeRate(rateName, ratesByRateName);
                calculatedRates.add(calculatedRate);
                log.info("Calculated {}: bid={}, ask={}, timestamp={}",
                        calculatedRate.getRateName(),
                        calculatedRate.getFields().getBid(),
                        calculatedRate.getFields().getAsk(),
                        calculatedRate.getFields().getTimestamp());
            } catch (FormulaEngineException e) {
                throw new CalculationException("Error calculating '" + rateName + "'", e);
            }
        }
        return calculatedRates;
    }

    private Map<String, List<Rate>> groupRatesByName(Map<String, Map<String, Rate>> groupedRates) {
        Map<String, List<Rate>> ratesByRateName = new HashMap<>();
        for (String rateName : rateNames) {
            List<Rate> rateList = new ArrayList<>();
            for (Map.Entry<String, Map<String, Rate>> platformEntry : groupedRates.entrySet()) {
                String platform = platformEntry.getKey();
                Map<String, Rate> rateMap = platformEntry.getValue();
                if (rateMap.containsKey(rateName)) {
                    Rate rate = rateMap.get(rateName);
                    // Platform bilgisini rateName'e ekle
                    rateList.add(new Rate(platform + rateName, rate.getFields(), rate.getStatus()));
                }
            }
            ratesByRateName.put(rateName, rateList);
        }
        return ratesByRateName;
    }

    private Rate computeRate(String rateName, Map<String, List<Rate>> ratesByRateName) throws FormulaEngineException {
        Map<String, Object> context = new HashMap<>();
        context.put("calcName", rateName);

        // USDTRY verilerini ekle
        addRateFieldsToContext("USDTRY", ratesByRateName, context);

        // Çapraz kurlar için verileri ekle
        if (!"USDTRY".equals(rateName)) {
            addRateFieldsToContext(rateName, ratesByRateName, context);
        }

        log.trace("Context keys: {}", context.keySet());
        double[] result = DynamicFormulaService.calculate(context);

        String resultName = rateName.endsWith("USD") && !rateName.equals("USDTRY")
                ? rateName.substring(0, rateName.length() - 3) + "TRY"
                : rateName;

        return new Rate(
                resultName,
                new RateFields(result[0], result[1], System.currentTimeMillis()),
                new RateStatus(true, true)
        );
    }

    private void addRateFieldsToContext(String rateName, Map<String, List<Rate>> ratesByRateName, Map<String, Object> context) {
        String camelCaseRateName = rateName.substring(0, 1).toUpperCase() + rateName.substring(1).toLowerCase();
        for (Rate rate : ratesByRateName.getOrDefault(rateName, List.of())) {
            String fullName = rate.getRateName(); // Örn. TCP_PLATFORMUSDTRY
            if (!fullName.endsWith(rateName)) {
                log.debug("Invalid rateName format: {} does not end with {}", fullName, rateName);
                continue;
            }
            String contextKey = fullName.substring(0, fullName.length() - rateName.length()) + camelCaseRateName;
            context.put(contextKey + "Bid", rate.getFields().getBid());
            context.put(contextKey + "Ask", rate.getFields().getAsk());
        }
    }
}