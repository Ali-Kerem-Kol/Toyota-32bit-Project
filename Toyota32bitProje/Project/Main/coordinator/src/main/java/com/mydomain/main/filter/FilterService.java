package com.mydomain.main.filter;

import com.mydomain.main.model.Rate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * {@code FilterService}, uygulamanın çalıştığı anda `config.json` dosyasından hangi filtrelerin
 * aktif olduğunu okur, bu filtreleri dinamik olarak oluşturur ve gelen verileri bu filtrelere
 * göre kontrol eder. `IRateFilter` arayüzünü uygulayan filtre sınıflarını yükler ve platform-rate
 * eşleşmelerine göre filtreleme yapar.
 *
 * <p>Hizmetin temel işleyişi:
 * <ul>
 *   <li>Konfigürasyon JSON’undan filtre sınıflarını ve platform-rate atamalarını yükler.</li>
 *   <li>Her filtre için reflection kullanılarak no-arg constructor ile nesne oluşturulur.</li>
 *   <li>Gelen veriler, tüm aktif filtrelerden geçer; herhangi bir filtre reddederse işlem sonlanır.</li>
 * </ul>
 * </p>
 *
 * <p><b>Özellikler:</b>
 * <ul>
 *   <li>Filtrelerin etkin/devre dışı durumu konfigürasyondan kontrol edilir.</li>
 *   <li>Loglama için Apache Log4j ile hata ayıklama ve izleme seviyeleri desteklenir.</li>
 *   <li>Filtrelerin platform ve rate bazlı uygulanması esnek bir yapı sunar.</li>
 * </ul>
 * </p>
 *
 * @author Ali Kerem Kol
 * @version 1.0
 * @since 2025-06-07
 */
public class FilterService {

    private static final Logger log = LogManager.getLogger(FilterService.class);

    private final List<IRateFilter> filters = new ArrayList<>();

    /**
     * {@code FilterService}’i başlatır ve belirtilen JSON konfigürasyonundan filtreleri yükler.
     * Her filtre sınıfı için reflection kullanılarak nesne oluşturulur ve platform-rate atamaları yapılır.
     *
     * @param filtersJson Filtrelerin konfigürasyonunu içeren JSON nesnesi,
     *                    null ise hata loglanır ve filtre yükleme başarısız olur
     */
    public FilterService(JSONObject filtersJson) {

        for (String key : filtersJson.keySet()) {
            JSONObject obj = filtersJson.getJSONObject(key);
            if (!obj.optBoolean("enabled", true)) continue;

            String className = obj.getString("className");
            JSONObject plats  = obj.getJSONObject("platforms");
            Map<String, Set<String>> platMap = parsePlatformRateMap(plats);

            try {
                // 1) Sınıfı yükle, no-arg ctor çağır
                Class<?> cls = Class.forName(className);
                if (!IRateFilter.class.isAssignableFrom(cls)) {
                    log.error("{} does not implement IRateFilter, skipped", className);
                    continue;
                }
                IRateFilter filter = (IRateFilter) cls.getDeclaredConstructor().newInstance();

                // 2) Platform-rate listesini enjekte et
                filter.setPlatformAssignments(platMap);
                filters.add(filter);

                log.info("Loaded filter {} → platforms={}", className, platMap.keySet());

            } catch (Exception e) {
                log.error("Cannot load filter {}: {}", className, e.getMessage(), e);
            }
        }
    }

    /**
     * JSON konfigürasyonundan platform-rate eşleşmelerini ayrıştırır.
     * Her platform için geçerli rate’leri bir HashSet içinde toplar.
     *
     * @param json Platform-rate eşleşmelerini içeren JSON nesnesi,
     *             null veya geçersiz formatta ise boş bir Map döndürülür
     * @return Platformlara göre rate’lerin eşlendiği Map nesnesi
     */
    private Map<String, Set<String>> parsePlatformRateMap(JSONObject json) {
        Map<String, Set<String>> result = new HashMap<>();

        for (String platform : json.keySet()) {
            JSONObject platformObj = json.optJSONObject(platform);
            if (platformObj == null || !platformObj.has("rates")) {
                log.warn("Filter config: '{}' için 'rates' alanı eksik veya geçersiz, atlanıyor.", platform);
                continue;
            }

            JSONArray rateArray = platformObj.optJSONArray("rates");
            if (rateArray == null) {
                log.warn("Filter config: '{}' için 'rates' dizisi null, atlanıyor.", platform);
                continue;
            }

            Set<String> rateSet = new HashSet<>();
            for (int i = 0; i < rateArray.length(); i++) {
                String rate = rateArray.optString(i, null);
                if (rate != null && !rate.isEmpty()) {
                    rateSet.add(rate);
                } else {
                    log.warn("Filter config: platform='{}' içinde boş veya geçersiz rate bulundu, atlanıyor.", platform);
                }
            }

            if (!rateSet.isEmpty()) {
                result.put(platform, rateSet);
            } else {
                log.warn("Filter config: platform='{}' için geçerli rate bulunamadı, atlanıyor.", platform);
            }
        }

        return result;
    }

    /**
     * Tüm yüklü filtreleri sırayla uygular ve verinin kabul edilip edilmeyeceğine karar verir.
     * Herhangi bir filtre reddederse false döndürülür.
     *
     * @param platformName Verinin geldiği platform adı (örnek: "TCP_PLATFORM")
     * @param rateName Döviz kuru adı (örnek: "USDTRY")
     * @param last Cache'teki son kabul edilen veri, null olabilir
     * @param candidate Yeni gelen ve değerlendirilecek veri, null ise false döndürülür
     * @param history Platform + rate'e ait geçmiş veri listesi, null olabilir
     * @return Eğer tüm filtreler veri kabul ederse true, aksi halde false
     */
    public boolean applyAllFilters(String platformName, String rateName, Rate last, Rate candidate, List<Rate> history) {
        for (IRateFilter filter : filters) {
            try {
                boolean accepted = filter.shouldAccept(platformName, rateName, last, candidate, history);
                if (!accepted) {
                    log.warn("❌ {} rejected rate (platformName={}, rateName={})",
                            filter.getClass().getSimpleName(), platformName, rateName);
                    return false;
                } else {
                    log.info("✅ {} passed (platformName={}, rateName={})",
                            filter.getClass().getSimpleName(), platformName, rateName);
                }
            } catch (Exception e) {
                log.error("❌ Exception occurred in {} for platformName={}, rateName={} → {}",
                        filter.getClass().getSimpleName(), platformName, rateName, e.getMessage(), e);
                return false;
            }
        }
        log.info("✅ All filters passed for rate (platformName={}, rateName={}): {}", platformName, rateName, candidate);
        return true;
    }

    /**
     * RateCache’in kullandığı tüm filtreleri döner.
     * Bu metod, filtrelerin dışa aktarımı için kullanılır.
     *
     * @return Tüm yüklü filtrelerin immutable bir listesi
     */
    public List<IRateFilter> getFilters() {
        return filters;
    }
}
