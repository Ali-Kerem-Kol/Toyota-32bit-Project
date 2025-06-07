package com.mydomain.main.filter;

import com.mydomain.main.model.Rate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * Uygulamanın çalıştığı anda config.json üzerinden hangi filtrelerin
 * aktif olduğunu okur, bunları oluşturur ve gelen verileri bu filtrelere
 * göre kontrol eder.
 */
public class FilterService {

    private static final Logger log = LogManager.getLogger(FilterService.class);

    private final List<IRateFilter> filters = new ArrayList<>();

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

    /** RateCache’in kullandığı tüm filtreleri döner */
    public List<IRateFilter> getFilters() {
        return filters;
    }
}
