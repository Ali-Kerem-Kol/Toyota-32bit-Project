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
 * {@code RateCalculatorService}, platformlardan gelen ham kurları işleyerek istenen kurların
 * dinamik JavaScript formülleriyle hesaplanmasını sağlar. Bu sınıf, kurların gruplanması,
 * USDTRY'nin zorunlu kontrolü ve çapraz kur hesaplamaları gibi temel işlemleri gerçekleştirir.
 *
 * <p>Hesaplama süreci şu adımları içerir:
 * <ul>
 *   <li>Platform bazında gelen veriler (örn. TCP_PLATFORMUSDTRY) rateName'e göre gruplanır.</li>
 *   <li>USDTRY kurları her hesaplama için zorunlu bir temel veri olarak kullanılır.</li>
 *   <li>Çapraz kurlar (örn. EURUSD) için ek veri gereksinimleri kontrol edilir ve hesaplanır.</li>
 * </ul>
 * </p>
 *
 * <p><b>Özellikler:</b>
 * <ul>
 *   <li>Dış servis bağımlılığı (Redis/Kafka) yoktur; yalnızca içsel veri yapıları kullanılır.</li>
 *   <li>Hata yönetimi için {@link CalculationException} ve {@link FormulaEngineException} istisnaları fırlatılır.</li>
 *   <li>Loglama için Apache Log4j kullanılır ve hata ayıklama için detaylı log mesajları sağlanır.</li>
 * </ul>
 * </p>
 *
 * @author Ali Kerem Kol
 * @version 1.0
 * @since 2025-06-07
 */
public class RateCalculatorService {
    private static final Logger log = LogManager.getLogger(RateCalculatorService.class);
    private final Set<String> rateNames;

    /**
     * {@code RateCalculatorService} nesnesini oluşturur.
     * Hesaplanacak kur adlarının (rateNames) bir kümesini alarak nesneyi başlatır.
     * Bu küme, sistemin hangi kurları işleyeceğini belirler (örn. {"USDTRY", "EURUSD"}).
     *
     * @param rateNames Hesaplanacak kur adlarının değiştirilemez kümesi
     *                  (null veya boş olmamalı, aksi halde IllegalArgumentException fırlatılır)
     * @throws IllegalArgumentException Eğer rateNames null veya boş ise
     */
    public RateCalculatorService(Set<String> rateNames) {
        this.rateNames = rateNames;
    }

    /**
     * Platformlardan gelen ham kurları işleyerek hesaplanmış kurların listesini döndürür.
     * Eğer giriş verisi null veya boşsa, boş bir liste döner. USDTRY verisi mevcut değilse
     * hesaplama işlemi iptal edilir ve loglanır. Her başarılı hesaplama loglanır.
     *
     * @param groupedRates Platform bazında gruplanmış ham kurlar
     *                     (Map<Platform, Map<RateName, Rate>>, null veya boş olabilir)
     * @return Hesaplanmış kurların List<Rate> türünde listesi, boş liste dönebilir
     * @throws CalculationException JavaScript motorunda hata oluşursa veya hesaplama başarısız olursa
     */
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

    /**
     * Platform bazında gelen kurları, rateName'e göre gruplandırır.
     * Her platformdan gelen veriler (örn. TCP_PLATFORMUSDTRY) bir liste halinde birleştirilir
     * ve platform bilgisi rateName'e eklenerek yeni bir Rate nesnesi oluşturulur.
     *
     * @param groupedRates Platform bazında gruplanmış ham kurlar
     *                     (Map<Platform, Map<RateName, Rate>>, null veya boş olabilir)
     * @return RateName'e göre gruplanmış Rate listelerini içeren Map
     *         (Map<RateName, List<Rate>>, her zaman dolu bir map döner)
     */
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

    /**
     * Belirli bir kur için hesaplama yapar ve sonucu bir Rate nesnesi olarak döndürür.
     * USDTRY her zaman context'e eklenir, çapraz kurlar (örn. EURUSD) için ek veri işlenir.
     * Hesaplama, DynamicFormulaService üzerinden JavaScript motoruyla gerçekleştirilir.
     *
     * @param rateName Hesaplanacak kur adı (örn. "EURUSD" veya "USDTRY")
     * @param ratesByRateName Gruplanmış Rate listeleri (Map<RateName, List<Rate>>)
     * @return Hesaplanmış Rate nesnesi, bid/ask değerleri ve zaman damgası ile
     * @throws FormulaEngineException JavaScript hesaplama motorunda hata oluşursa
     */
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

    /**
     * Context'e rateName'e ait bid ve ask verilerini ekler.
     * Anahtarlar camelCase formatında oluşturulur ve format kontrolü yapılır.
     * Bu metod, belirtilen kur adına (rateName) karşılık gelen verileri (örneğin, platform bazlı
     * bid/ask değerleri) context map'ine ekler. Veriler, ratesByRateName'den alınır ve fullName
     * ile rateName'in uyumluluğu kontrol edilerek contextKey (örn. TCP_PLATFORMUsdtryBid)
     * oluşturulur. Uyumsuz formatlar loglanır ve işlenmez.
     *
     * @param rateName Verilerin ekleneceği kur adı (örn. "USDTRY" veya "EURUSD"), bu parametre
     *                 metodun hangi kurun verilerini (bid/ask) context'e ekleyeceğini belirler
     * @param ratesByRateName Gruplanmış Rate listeleri, platform bazında organize edilmiş veriler
     *                        (Map<'String', List<'Rate'>>)
     * @param context Verilerin ekleneceği context map, hesaplama için DynamicFormulaService'e
     *                iletilir ve JavaScript motorunda kullanılır
     */
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