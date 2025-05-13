package com.mydomain.main.service;

import com.mydomain.main.config.ConfigReader;
import com.mydomain.main.model.Rate;
import com.mydomain.main.model.RateFields;
import com.mydomain.main.model.RateStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Ham kurları Redis’ten alır → JS formülünü çalıştırır → sonucu Redis’e yazar.
 * •   USDTRY ya da çaprazlar için en az **bir** platform yeterlidir.
 * •   Eksik platformlar yalnızca WARN loglanır; hesaplama devam eder.
 * •   RateStatus dursun diye true/true set edilir.
 */
public class RateCalculatorService {

    private static final Logger logger = LogManager.getLogger(RateCalculatorService.class);
    private final RedisService redisService;

    /* --- konfigürasyon bir kez okunur ---------------------------------- */
    private final Set<String>               shortNames;     // USDTRY, EURUSD …
    private final Map<String,List<String>>  fullByShort;    // USDTRY→[PF1_USDTRY,…]

    public RateCalculatorService(RedisService redisService) {
        this.redisService = redisService;

        Set<String> full = ConfigReader.getSubscribeRates();
        this.shortNames  = ConfigReader.getSubscribeRatesShort();
        this.fullByShort = full.stream()
                .collect(Collectors.groupingBy(fn -> fn.substring(fn.indexOf('_') + 1)));
    }

    /* ------------------------------------------------------------------ */
    public Map<String, Rate> calculate() {

        if (!hasAnyRate("USDTRY")) {
            logger.warn("❌ Hiç USDTRY verisi yok; hesaplama atlandı.");
            return Collections.emptyMap();
        }

        Map<String, Rate> calculated = new HashMap<>();

        for (String sn : shortNames) {
            if (!hasAnyRate(sn) && !sn.equals("USDTRY")) {
                logger.warn("💡 {} verisi yok; atlanıyor.", sn);
                continue;
            }
            try {
                Rate calc = compute(sn);
                calculated.put(calc.getRateName(), calc);
                redisService.putCalculatedRate(calc.getRateName(), calc);
                logger.info("🔹 {} => bid={}, ask={}",
                        calc.getRateName(),
                        calc.getFields().getBid(),
                        calc.getFields().getAsk());
            } catch (Exception e) {
                logger.error("❌ {} hesaplanırken hata: {}", sn, e.getMessage(), e);
            }
        }
        return calculated;
    }

    /* ---------------------------- helpers ------------------------------ */
    private boolean hasAnyRate(String shortName) {
        return fullByShort.getOrDefault(shortName, List.of())
                .stream()
                .anyMatch(fn -> redisService.getRawRate(fn) != null);
    }

    private Rate compute(String shortName) {

        /* ① JS context’i kur */
        Map<String,Object> ctx = new HashMap<>();
        ctx.put("calcName", shortName);

        /*   USDTRY her formülde gerekir */
        fullByShort.get("USDTRY").forEach(full -> putIfPresent(ctx, full, "Usdtry"));

        /*   Çapraz gerekiyorsa onu da ekle */
        if (!"USDTRY".equals(shortName)) {
            String camel = shortName.substring(0,1).toUpperCase() +
                    shortName.substring(1).toLowerCase();          // EURUSD→Eurusd
            fullByShort.get(shortName).forEach(full -> putIfPresent(ctx, full, camel));
        }

        /* ② JS hesabı */
        double[] result = DynamicFormulaService.calculate(ctx);

        /* ③ Sonuç adı (EURTRY, GBPTRY …) */
        String resultName = shortName.endsWith("USD") && !shortName.equals("USDTRY")
                ? shortName.substring(0,3) + "TRY"
                : shortName;

        return new Rate(
                resultName,
                new RateFields(result[0], result[1], System.currentTimeMillis()),
                new RateStatus(true, true)    // “dursun ama işlevsiz”
        );
    }

    /** Redis’te varsa ctx’e  pfX+CamelBid/Ask anahtarlarını ekler. */
    private void putIfPresent(Map<String,Object> ctx, String full, String camel) {
        Rate r = redisService.getRawRate(full);
        if (r == null) return;
        String pf = full.substring(0, full.indexOf('_')).toLowerCase();   // pf1, pf2…
        ctx.put(pf + camel + "Bid", r.getFields().getBid());
        ctx.put(pf + camel + "Ask", r.getFields().getAsk());
    }
}
