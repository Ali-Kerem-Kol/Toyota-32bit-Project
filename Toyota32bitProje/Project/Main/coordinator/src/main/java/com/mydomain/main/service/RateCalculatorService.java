package com.mydomain.main.service;

import com.mydomain.main.model.Rate;
import com.mydomain.main.model.RateFields;
import com.mydomain.main.model.RateStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * RateCalculator => Tüm hesaplamaları 'DynamicFormulaEngine' ile JS tarafında yapar.
 * Dokümandaki formüller myFormula.js içinde yer alıyor.
 */
public class RateCalculatorService {

    private static final Logger logger = LogManager.getLogger(RateCalculatorService.class);


    public Map<String, Rate> calculateRates(RedisService redis) {
        Map<String, Rate> rates = new HashMap<>();
        try {
            // USDTRY hesaplaması için gerekli verileri kontrol ediyoruz.
            Rate pf1UsdTry = redis.getRawRate("PF1_USDTRY");
            Rate pf2UsdTry = redis.getRawRate("PF2_USDTRY");
            if (pf1UsdTry == null || pf2UsdTry == null ||
                    !pf1UsdTry.getStatus().isActive() || !pf2UsdTry.getStatus().isActive() ||
                    !pf1UsdTry.getStatus().isUpdated() || !pf2UsdTry.getStatus().isUpdated()) {
                logger.warn("USDTRY hesaplaması yapılamıyor: PF1_USDTRY veya PF2_USDTRY eksik ya da güncel değil.");
            } else {
                Rate usdTry = calculateUsdTry(pf1UsdTry, pf2UsdTry);
                redis.putCalculatedRate("USDTRY", usdTry);
                logger.info("🔹 USDTRY => {}", usdTry);
                rates.put("USDTRY",usdTry);
            }

            // EURTRY hesaplaması için kontrol
            Rate pf1EurUsd = redis.getRawRate("PF1_EURUSD");
            Rate pf2EurUsd = redis.getRawRate("PF2_EURUSD");
            if (pf1UsdTry == null || pf2UsdTry == null || pf1EurUsd == null || pf2EurUsd == null ||
                    !pf1UsdTry.getStatus().isActive() || !pf2UsdTry.getStatus().isActive() ||
                    !pf1UsdTry.getStatus().isUpdated() || !pf2UsdTry.getStatus().isUpdated() ||
                    !pf1EurUsd.getStatus().isActive() || !pf2EurUsd.getStatus().isActive() ||
                    !pf1EurUsd.getStatus().isUpdated() || !pf2EurUsd.getStatus().isUpdated()) {
                logger.warn("EURTRY hesaplaması yapılamıyor: PF1_EURUSD veya PF2_EURUSD veya USDTRY için gerekli rate'ler eksik ya da güncel değil.");
            } else {
                Rate eurTry = calculateEurTry(pf1UsdTry, pf2UsdTry, pf1EurUsd, pf2EurUsd);
                redis.putCalculatedRate("EURTRY", eurTry);
                logger.info("🔹 EURTRY => {}", eurTry);
                rates.put("EURTRY",eurTry);
            }

            // GBPTRY hesaplaması için kontrol
            Rate pf1GbpUsd = redis.getRawRate("PF1_GBPUSD");
            Rate pf2GbpUsd = redis.getRawRate("PF2_GBPUSD");
            if (pf1UsdTry == null || pf2UsdTry == null || pf1GbpUsd == null || pf2GbpUsd == null ||
                    !pf1UsdTry.getStatus().isActive() || !pf2UsdTry.getStatus().isActive() ||
                    !pf1UsdTry.getStatus().isUpdated() || !pf2UsdTry.getStatus().isUpdated() ||
                    !pf1GbpUsd.getStatus().isActive() || !pf2GbpUsd.getStatus().isActive() ||
                    !pf1GbpUsd.getStatus().isUpdated() || !pf2GbpUsd.getStatus().isUpdated()) {
                logger.warn("GBPTRY hesaplaması yapılamıyor: PF1_GBPUSD veya PF2_GBPUSD veya USDTRY için gerekli rate'ler eksik ya da güncel değil.");
            } else {
                Rate gbpTry = calculateGbpTry(pf1UsdTry, pf2UsdTry, pf1GbpUsd, pf2GbpUsd);
                redis.putCalculatedRate("GBPTRY", gbpTry);
                logger.info("🔹 GBPTRY => {}", gbpTry);
                rates.put("GBPTRY",gbpTry);
            }
        } catch (Exception e) {
            logger.error("❌ Error in calculateRates(): {}", e.getMessage(), e);
        }
        return rates;
    }


    /**
     * USDTRY =>
     *   doc: (pf1UsdTry.bid + pf2UsdTry.bid)/2, (pf1UsdTry.ask + pf2UsdTry.ask)/2
     */
    public Rate calculateUsdTry(Rate pf1UsdTry, Rate pf2UsdTry) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("calcName", "USDTRY");

        // pf1UsdTry => pf1Bid, pf1Ask
        ctx.put("pf1Bid", pf1UsdTry.getFields().getBid());
        ctx.put("pf1Ask", pf1UsdTry.getFields().getAsk());

        // pf2UsdTry => pf2Bid, pf2Ask
        ctx.put("pf2Bid", pf2UsdTry.getFields().getBid());
        ctx.put("pf2Ask", pf2UsdTry.getFields().getAsk());

        double[] result = DynamicFormulaService.calculate(ctx);
        double bid = result[0];
        double ask = result[1];

        Rate rate = new Rate("USDTRY",
                new RateFields(bid, ask, System.currentTimeMillis()),
                new RateStatus(true, true));
        logger.debug("Calculated USDTRY => bid={}, ask={}", bid, ask);
        return rate;
    }

    /**
     * EURTRY =>
     *   doc:
     *     usdMid = ((pf1UsdTry.bid+pf2UsdTry.bid)/2 + (pf1UsdTry.ask+pf2UsdTry.ask)/2 ) / 2
     *     EURTRY.bid = usdMid * avg(EURUSD.bid)
     *     ...
     */
    public Rate calculateEurTry(Rate pf1UsdTry, Rate pf2UsdTry, Rate pf1EurUsd, Rate pf2EurUsd) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("calcName", "EURTRY");

        // For the 'usdMid' part
        ctx.put("pf1Bid", pf1UsdTry.getFields().getBid());
        ctx.put("pf1Ask", pf1UsdTry.getFields().getAsk());
        ctx.put("pf2Bid", pf2UsdTry.getFields().getBid());
        ctx.put("pf2Ask", pf2UsdTry.getFields().getAsk());

        // EURUSD part
        ctx.put("pf1EurUsdBid", pf1EurUsd.getFields().getBid());
        ctx.put("pf1EurUsdAsk", pf1EurUsd.getFields().getAsk());
        ctx.put("pf2EurUsdBid", pf2EurUsd.getFields().getBid());
        ctx.put("pf2EurUsdAsk", pf2EurUsd.getFields().getAsk());

        double[] result = DynamicFormulaService.calculate(ctx);
        double bid = result[0];
        double ask = result[1];

        Rate rate = new Rate("EURTRY",
                new RateFields(bid, ask, System.currentTimeMillis()),
                new RateStatus(true, true));
        logger.debug("Calculated EURTRY => bid={}, ask={}", bid, ask);
        return rate;
    }

    /**
     * GBPTRY =>
     *   doc:
     *     usdMid = ...
     *     GBPTRY.bid = usdMid * avg(GBPUSD.bid)
     */
    public Rate calculateGbpTry(Rate pf1UsdTry, Rate pf2UsdTry, Rate pf1GbpUsd, Rate pf2GbpUsd) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("calcName", "GBPTRY");

        // For the 'usdMid' part
        ctx.put("pf1Bid", pf1UsdTry.getFields().getBid());
        ctx.put("pf1Ask", pf1UsdTry.getFields().getAsk());
        ctx.put("pf2Bid", pf2UsdTry.getFields().getBid());
        ctx.put("pf2Ask", pf2UsdTry.getFields().getAsk());

        // GBPUSD part
        ctx.put("pf1GbpUsdBid", pf1GbpUsd.getFields().getBid());
        ctx.put("pf1GbpUsdAsk", pf1GbpUsd.getFields().getAsk());
        ctx.put("pf2GbpUsdBid", pf2GbpUsd.getFields().getBid());
        ctx.put("pf2GbpUsdAsk", pf2GbpUsd.getFields().getAsk());

        double[] result = DynamicFormulaService.calculate(ctx);
        double bid = result[0];
        double ask = result[1];

        Rate rate = new Rate("GBPTRY",
                new RateFields(bid, ask, System.currentTimeMillis()),
                new RateStatus(true, true));
        logger.debug("Calculated GBPTRY => bid={}, ask={}", bid, ask);
        return rate;
    }

}
