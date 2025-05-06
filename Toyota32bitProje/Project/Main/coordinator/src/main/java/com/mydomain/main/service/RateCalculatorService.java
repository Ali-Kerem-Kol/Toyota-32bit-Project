package com.mydomain.main.service;

import com.mydomain.main.config.ConfigReader;
import com.mydomain.main.model.Rate;
import com.mydomain.main.model.RateFields;
import com.mydomain.main.model.RateStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Kur hesaplamalarını yapan servis
 * Redis'ten gelen ham kurları alır
 * DynamicFormulaService aracılığıyla JavaScript ile tanımlı formülleri uygular
 * Hesaplanan kurları Redis'e kaydeder ve geriye döner
 */
public class RateCalculatorService {

    private static final Logger logger = LogManager.getLogger(RateCalculatorService.class);

    private final RedisService redisService;

    public RateCalculatorService(RedisService redisService) {
        this.redisService = redisService;
    }

    /**
     * Redis’teki ham kurları alır, config.json’daki abone listesine göre gruplar oluşturur,
     * JS formüllerini çalıştırarak hem direct (USDTRY) hem cross-rate (EURTRY, GBPTRY, …)
     * kurlarını hesaplar, sonuçları Redis’e kaydeder ve geri döner.
     *
     * 1. ConfigReader’dan PFx_… formatındaki tam adları ve kısa adları (USDTRY, EURUSD, …) okur.
     * 2. Tam adları kısa adlara göre gruplayarak direct ve cross-rate verilerini ayırır.
     * 3. Tüm platform verilerinin aktif ve güncel olduğunu doğrular.
     * 4. Her bir kısa ad için DynamicFormulaService ile JavaScript formülünü çalıştırıp
     *    ortalama bid/ask değerlerini hesaplar.
     * 5. Hesaplanan Rate nesnelerini Redis’e yazar ve bir Map<String, Rate> içinde döner.
     *
     * @return Hesaplanan kurların kısa adlarını (USDTRY, EURTRY, GBPTRY, vb.) Rate nesneleriyle eşleyen bir Map
     */
    public Map<String, Rate> calculateRates() {
        // Döndürülecek sonuç haritası: kısa adı → hesaplanmış Rate
        Map<String, Rate> calculated = new HashMap<>();

        // 1) ConfigReader’dan tüm tam ve kısa adları al
        Set<String> fullNames  = ConfigReader.getSubscribeRates();      // Örn. PF1_USDTRY, PF2_USDTRY, PF1_EURUSD, …
        Set<String> shortNames = ConfigReader.getSubscribeRatesShort(); // Örn. USDTRY, EURUSD, GBPUSD, …

        // 2) Tam adları arkasındaki kısa ada göre grupla (PF1_USDTRY→USDTRY, PF1_EURUSD→EURUSD, …)
        Map<String, List<String>> byShort = fullNames.stream()
                .collect(Collectors.groupingBy(full ->
                        full.substring(full.indexOf('_') + 1)  // "_" sonrası kısmı al
                ));

        // 3) “direct” USDTRY grubunu önceden oku (cross‐rate’ler için ihtiyaç var)
        List<String> usdGroup = byShort.get("USDTRY");
        Map<String, Rate> rawUsd = usdGroup.stream()
                .collect(Collectors.toMap(fn -> fn,
                        fn -> redisService.getRawRate(fn)
                ));

        // 4) Tüm kısa adlar (USDTRY, EURUSD, GBPUSD…) için döngü
        for (String shortName : shortNames) {
            // 4a) Cross‐rate mi? (“EURUSD”/“GBPUSD”) → sonucu “EURTRY”/“GBPTRY” olarak kaydedeceğiz
            boolean isCross = shortName.endsWith("USD") && !shortName.equals("USDTRY");
            String resultName = isCross
                    ? shortName.substring(0,3) + "TRY"   // EURUSD→EURTRY, GBPUSD→GBPTRY
                    : shortName;                         // USDTRY→USDTRY

            // 4b) O kısma ait tam ad listesi
            List<String> groupFulls = byShort.get(shortName);
            if (groupFulls == null || groupFulls.isEmpty()) {
                logger.warn("Config içinde {} için tanım yok.", shortName);
                continue;
            }

            // 5) O grubun ham Rate’lerini oku
            Map<String, Rate> rawGroup = groupFulls.stream()
                    .collect(Collectors.toMap(fn -> fn,
                            fn -> redisService.getRawRate(fn)
                    ));

            // 6) Validasyon: hem USDTRY hem de bu grubun tüm Rate’leri aktif ve güncel olmalı
            boolean allOk = Stream.concat(
                            rawUsd.values().stream(),
                            rawGroup.values().stream()
                    )
                    .allMatch(r -> r != null && r.getStatus().isActive() && r.getStatus().isUpdated());
            if (!allOk) {
                logger.warn("{} hesaplanamıyor: bazı ham kurlar eksik veya güncel değil.", shortName);
                continue;
            }

            // 7) JS formülü için context haritası oluştur
            Map<String, Object> ctx = new HashMap<>();
            ctx.put("calcName", shortName);  // JS’e hangi shortName’i (“USDTRY”/“EURUSD”/“GBPUSD”) gönderiyoruz

            // 7a) USDTRY ham verilerini context’e ekle: pf1UsdtryBid, pf2UsdtryAsk vb.
            for (Map.Entry<String, Rate> e : rawUsd.entrySet()) {
                String full     = e.getKey();                   // Örn. "PF1_USDTRY"
                Rate rate       = e.getValue();
                String provider = full.substring(0, full.indexOf('_')).toLowerCase(); // "pf1" veya "pf2"
                String keyBase  = "Usdtry";                     // sabit, çünkü direct USDTRY
                ctx.put(provider + keyBase + "Bid", rate.getFields().getBid());
                ctx.put(provider + keyBase + "Ask", rate.getFields().getAsk());
            }

            // 7b) Cross‐rate grubunu da ekle: pf1EurusdBid, pf2EurusdAsk vb.
            for (Map.Entry<String, Rate> e : rawGroup.entrySet()) {
                String full     = e.getKey();                   // Örn. "PF1_EURUSD"
                Rate rate       = e.getValue();
                String provider = full.substring(0, full.indexOf('_')).toLowerCase(); // "pf1" veya "pf2"
                // Kısa adı “Eurusd” veya “Gbpusd” formatına çevir
                String currency = shortName.substring(0,1).toUpperCase()
                        + shortName.substring(1).toLowerCase();
                ctx.put(provider + currency + "Bid", rate.getFields().getBid());
                ctx.put(provider + currency + "Ask", rate.getFields().getAsk());
            }

            // 8) JavaScript formülünü çalıştır
            double[] result = DynamicFormulaService.calculate(ctx);

            // 9) Sonucu Rate’ye dönüştürüp Redis’e ve sonuç haritasına ekle
            Rate calc = new Rate(
                    resultName,
                    new RateFields(result[0], result[1], System.currentTimeMillis()),
                    new RateStatus(true, true)
            );
            redisService.putCalculatedRate(resultName, calc);
            calculated.put(resultName, calc);

            // 10) Log’la
            logger.info("🔹 {} => bid={}, ask={}", resultName, result[0], result[1]);
        }

        return calculated;
    }

}
