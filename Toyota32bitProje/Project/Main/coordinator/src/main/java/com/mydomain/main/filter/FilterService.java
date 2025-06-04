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

    private static final Logger logger = LogManager.getLogger(FilterService.class);

    private final List<IRateFilter> filters = new ArrayList<>();

    public FilterService(JSONObject filtersJson) {
        // ---------------- JumpThresholdFilter ----------------
        if (filtersJson.getJSONObject("jumpThresholdFilter").getBoolean("enabled")) {
            double maxJumpPercent = filtersJson.getJSONObject("jumpThresholdFilter").getDouble("maxJumpPercent");
            JSONObject platformAssignments = filtersJson.getJSONObject("jumpThresholdFilter").getJSONObject("platforms");

            Map<String, Set<String>> platformRateMap = parsePlatformRateMap(platformAssignments);

            filters.add(new JumpThresholdFilter(maxJumpPercent, platformRateMap));
            logger.info("JumpThresholdFilter enabled → maxJumpPercent={}, platforms={}", maxJumpPercent, platformRateMap.keySet());
        }
        // ---------------- MovingAverageFilter ----------------
        if (filtersJson.getJSONObject("movingAverageFilter").getBoolean("enabled")) {
            double maxJumpPercent = filtersJson.getJSONObject("movingAverageFilter").getDouble("maxJumpPercent");
            JSONObject platformAssignments = filtersJson.getJSONObject("movingAverageFilter").getJSONObject("platforms");

            Map<String, Set<String>> platformRateMap = parsePlatformRateMap(platformAssignments);

            filters.add(new MovingAverageFilter(maxJumpPercent, platformRateMap));
            logger.info("movingAverageFilter enabled → maxJumpPercent={}, platforms={}", maxJumpPercent, platformRateMap.keySet());
        }
    }

    private Map<String, Set<String>> parsePlatformRateMap(JSONObject json) {
        Map<String, Set<String>> result = new HashMap<>();

        for (String platform : json.keySet()) {
            JSONObject platformObj = json.optJSONObject(platform);
            if (platformObj == null || !platformObj.has("rates")) {
                logger.warn("Filter config: '{}' için 'rates' alanı eksik veya geçersiz, atlanıyor.", platform);
                continue;
            }

            JSONArray rateArray = platformObj.optJSONArray("rates");
            if (rateArray == null) {
                logger.warn("Filter config: '{}' için 'rates' dizisi null, atlanıyor.", platform);
                continue;
            }

            Set<String> rateSet = new HashSet<>();
            for (int i = 0; i < rateArray.length(); i++) {
                String rate = rateArray.optString(i, null);
                if (rate != null && !rate.isEmpty()) {
                    rateSet.add(rate);
                } else {
                    logger.warn("Filter config: platform='{}' içinde boş veya geçersiz rate bulundu, atlanıyor.", platform);
                }
            }

            if (!rateSet.isEmpty()) {
                result.put(platform, rateSet);
            } else {
                logger.warn("Filter config: platform='{}' için geçerli rate bulunamadı, atlanıyor.", platform);
            }
        }

        return result;
    }



    public boolean applyAllFilters(String platform, String rateName, Rate last, Rate candidate, List<Rate> history) {
        for (IRateFilter filter : filters) {
            try {
                boolean accepted = filter.shouldAccept(platform, rateName, last, candidate, history);
                if (!accepted) {
                    logger.warn("❌ FilterService: {} rejected rate (platform={}, rateName={})",
                            filter.getClass().getSimpleName(), platform, rateName);
                    return false;
                } else {
                    logger.trace("✅ FilterService: {} passed (platform={}, rateName={})",
                            filter.getClass().getSimpleName(), platform, rateName);
                }
            } catch (Exception e) {
                logger.error("FilterService: Exception occurred in {} for platform={}, rateName={} → {}",
                        filter.getClass().getSimpleName(), platform, rateName, e.getMessage(), e);
                return false;
            }
        }
        logger.trace("All filters passed for rate (platform={}, rateName={}): {}", platform, rateName, candidate);
        return true;
    }

    /** RateCache’in kullandığı tüm filtreleri döner */
    public List<IRateFilter> getFilters() {
        return filters;
    }
}
