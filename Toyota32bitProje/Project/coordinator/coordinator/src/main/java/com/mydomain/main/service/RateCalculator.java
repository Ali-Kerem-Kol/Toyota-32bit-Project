package com.mydomain.main.service;

import com.mydomain.main.model.Rate;
import com.mydomain.main.model.RateFields;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Dokümandaki statik formülleri uygular.
 */
public class RateCalculator {

    /**
     * USDTRY Hesaplar:
     * USDTRY.bid = (PF1_USDTRY.bid + PF2_USDTRY.bid) / 2
     * USDTRY.ask = (PF1_USDTRY.ask + PF2_USDTRY.ask) / 2
     */
    public static Rate calculateUsdTry(Rate pf1UsdTry, Rate pf2UsdTry) {
        double bid = (pf1UsdTry.getFields().getBid() + pf2UsdTry.getFields().getBid()) / 2.0;
        double ask = (pf1UsdTry.getFields().getAsk() + pf2UsdTry.getFields().getAsk()) / 2.0;

        // basitçe timestamp şu an
        long now = System.currentTimeMillis();

        // Rate nesnesi oluştur
        Rate usdTry = new Rate();
        usdTry.setRateName("USDTRY");
        usdTry.setFields(new RateFields(bid, ask, now));
        return usdTry;
    }

    /**
     * usdmid = ((PF1_USDTRY.bid + PF2_USDTRY.bid)/2 + (PF1_USDTRY.ask + PF2_USDTRY.ask)/2) / 2
     */
    public static double calculateUsdMid(Rate pf1UsdTry, Rate pf2UsdTry) {
        double avgBid = (pf1UsdTry.getFields().getBid() + pf2UsdTry.getFields().getBid()) / 2.0;
        double avgAsk = (pf1UsdTry.getFields().getAsk() + pf2UsdTry.getFields().getAsk()) / 2.0;
        return (avgBid + avgAsk) / 2.0;
    }

    /**
     * EURTRY.bid = usdmid * ((PF1_EURUSD.bid + PF2_EURUSD.bid)/2)
     * EURTRY.ask = usdmid * ((PF1_EURUSD.ask + PF2_EURUSD.ask)/2)
     */
    public static Rate calculateEurTry(Rate pf1EurUsd, Rate pf2EurUsd, double usdMid) {
        double bidUsd = (pf1EurUsd.getFields().getBid() + pf2EurUsd.getFields().getBid()) / 2.0;
        double askUsd = (pf1EurUsd.getFields().getAsk() + pf2EurUsd.getFields().getAsk()) / 2.0;

        double bid = usdMid * bidUsd;
        double ask = usdMid * askUsd;

        long now = System.currentTimeMillis();
        Rate eurTry = new Rate();
        eurTry.setRateName("EURTRY");
        eurTry.setFields(new RateFields(bid, ask, now));
        return eurTry;
    }

    /**
     * GBPTRY.bid = usdmid * ((PF1_GBPUSD.bid + PF2_GBPUSD.bid)/2)
     * GBPTRY.ask = usdmid * ((PF1_GBPUSD.ask + PF2_GBPUSD.ask)/2)
     */
    public static Rate calculateGbpTry(Rate pf1GbpUsd, Rate pf2GbpUsd, double usdMid) {
        double bidUsd = (pf1GbpUsd.getFields().getBid() + pf2GbpUsd.getFields().getBid()) / 2.0;
        double askUsd = (pf1GbpUsd.getFields().getAsk() + pf2GbpUsd.getFields().getAsk()) / 2.0;

        double bid = usdMid * bidUsd;
        double ask = usdMid * askUsd;

        long now = System.currentTimeMillis();
        Rate gbpTry = new Rate();
        gbpTry.setRateName("GBPTRY");
        gbpTry.setFields(new RateFields(bid, ask, now));
        return gbpTry;
    }
}
