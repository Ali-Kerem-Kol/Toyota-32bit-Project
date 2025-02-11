package com.mydomain.main.coordinator;

import com.mydomain.main.model.Rate;
import com.mydomain.main.model.RateFields;
import com.mydomain.main.model.RateStatus;
import com.mydomain.main.provider.RESTProvider;
import com.mydomain.main.provider.TCPProvider;
import com.mydomain.main.service.RateCalculator;
import com.mydomain.main.service.RateProducerService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class Coordinator implements CoordinatorInterface {

    private final RESTProvider restProvider;
    private final TCPProvider tcpProvider;

    // Kafka Producer'a erişmek için
    private final RateProducerService rateProducerService;

    // 📌 Platform verilerini saklamak için bir yapı
    private final Map<String, Rate> rateCache = new HashMap<>();

    // Coordinator'a artık RateProducerService parametresi eklendi
    public Coordinator(RESTProvider restProvider,
                       TCPProvider tcpProvider,
                       RateProducerService rateProducerService) {
        this.restProvider = restProvider;
        this.tcpProvider = tcpProvider;
        this.rateProducerService = rateProducerService;
    }

    @Override
    public void onConnect(String platformName, Boolean status) {
        System.out.println("🔗 " + platformName + " bağlantı durumu: " + (status ? "Bağlandı" : "Bağlantı Kesildi"));
    }

    @Override
    public void onDisConnect(String platformName, Boolean status) {
        System.out.println("🔴 " + platformName + " bağlantı kesildi.");
    }

    @Override
    public void onRateAvailable(String platformName, String rateName, Rate rate) {
        rateCache.put(rateName, rate);
        System.out.println("📈 Yeni Oran Kullanılabilir: " + rate);
        calculateRates(); // Her yeni gelen oranda hesaplama
    }

    @Override
    public void onRateUpdate(String platformName, String rateName, RateFields rateFields) {
        try {
            if (rateCache.containsKey(rateName)) {
                rateCache.get(rateName).setFields(rateFields);
            }
            System.out.println("📊 Oran Güncellendi: " + rateName + " -> " + rateFields);
            calculateRates(); // Her güncellemede hesaplama
        } catch (Exception e) {
            System.err.println("❌ Error in onRateUpdate: " + e.getMessage());
        }
    }

    @Override
    public void onRateStatus(String platformName, String rateName, RateStatus rateStatus) {
        if (rateCache.containsKey(rateName)) {
            rateCache.get(rateName).setStatus(rateStatus);
        }
        System.out.println("ℹ️ Oran Durumu Güncellendi: " + rateName + " -> " + rateStatus);
    }

    // ✅ **REST API'den Tek Seferlik Veri Çekme**
    public Rate fetchRateFromRest(String platformName, String rateName) {
        try {
            Rate rate = restProvider.fetchRate(platformName, rateName);
            if (rate != null) {
                onRateAvailable(platformName, rateName, rate);
            }
            return rate;
        } catch (RuntimeException ex) {
            System.err.println("❌ fetchRateFromRest error: " + ex.getMessage());
            return null;
        }
    }

    // ✅ **TCP için Abonelik Başlatma**
    public void subscribeToTcp(String platformName, String rateName) {
        tcpProvider.subscribe(platformName, rateName);
    }

    // ✅ **TCP için Abonelikten Çıkma**
    public void unsubscribeFromTcp(String platformName, String rateName) {
        tcpProvider.unsubscribe(platformName, rateName);
    }

    @Override
    public RESTProvider getRestProvider() {
        return this.restProvider;
    }

    @Override
    public TCPProvider getTcpProvider() {
        return this.tcpProvider;
    }

    @Override
    public Collection<Rate> getAllRates() {
        return rateCache.values();
    }

    @Override
    public Rate getRate(String rateName) {
        return rateCache.get(rateName);
    }

    // Bu metod, her yeni veri geldiğinde tetiklenebilir
    private void calculateRates() {

        try {
            // 1) PF1_USDTRY ve PF2_USDTRY var mı? => USDTRY hesapla
            Rate pf1UsdTry = rateCache.get("PF1_USDTRY");
            Rate pf2UsdTry = rateCache.get("PF2_USDTRY");
            Rate usdTry = null;

            if (pf1UsdTry != null && pf2UsdTry != null) {
                usdTry = RateCalculator.calculateUsdTry(pf1UsdTry, pf2UsdTry);
                rateCache.put("USDTRY", usdTry);
                System.out.println("🔹 USDTRY hesaplandı: " + usdTry);

                // Kafka'ya gönderelim
                sendRateToKafka("USDTRY", usdTry);
            }

            // 2) usdmid lazımsa tekrar al
            double usdMid = 0.0;
            if (pf1UsdTry != null && pf2UsdTry != null) {
                usdMid = RateCalculator.calculateUsdMid(pf1UsdTry, pf2UsdTry);
            }

            // 3) EURTRY
            Rate pf1EurUsd = rateCache.get("PF1_EURUSD");
            Rate pf2EurUsd = rateCache.get("PF2_EURUSD");
            if (pf1EurUsd != null && pf2EurUsd != null && usdMid != 0.0) {
                Rate eurTry = RateCalculator.calculateEurTry(pf1EurUsd, pf2EurUsd, usdMid);
                rateCache.put("EURTRY", eurTry);
                System.out.println("🔹 EURTRY hesaplandı: " + eurTry);

                // Kafka'ya gönderelim
                sendRateToKafka("EURTRY", eurTry);
            }

            // 4) GBPTRY
            Rate pf1GbpUsd = rateCache.get("PF1_GBPUSD");
            Rate pf2GbpUsd = rateCache.get("PF2_GBPUSD");
            if (pf1GbpUsd != null && pf2GbpUsd != null && usdMid != 0.0) {
                Rate gbpTry = RateCalculator.calculateGbpTry(pf1GbpUsd, pf2GbpUsd, usdMid);
                rateCache.put("GBPTRY", gbpTry);
                System.out.println("🔹 GBPTRY hesaplandı: " + gbpTry);

                // Kafka'ya gönderelim
                sendRateToKafka("GBPTRY", gbpTry);
            }
        } catch (Exception e) {
            System.err.println("❌ Error in calculateRates(): " + e.getMessage());
        }
    }

    /**
     *  Kafka'ya mesaj göndermek için basit yardımcı metod.
     *  Dokümanda "rateName|bid|ask|timestamp" formatı önerilmişti.
     */
    private void sendRateToKafka(String calcRateName, Rate calcRate) {
        double bid = calcRate.getFields().getBid();
        double ask = calcRate.getFields().getAsk();

        // ISO-8601 formatlı zaman damgası üret (UTC)
        String isoTimestamp = OffsetDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        // Örnek sonuç: "2025-02-10T14:18:32.123Z"

        // Dokümanda: "rateName|bid|ask|timestamp"
        String message = calcRateName + "|" + bid + "|" + ask + "|" + isoTimestamp;
        rateProducerService.sendRate(message);
    }
}

