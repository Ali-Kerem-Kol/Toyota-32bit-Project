package com.mydomain.main.filter;

import com.mydomain.main.model.Rate;
import com.mydomain.main.model.RateFields;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MovingAverageFilter:
 * Platform + RateName bazında çalışan filtre.
 * Tüm geçmiş verilerin ortalamasını alır. ±%X sapma varsa veri reddedilir.
 */
public class MovingAverageFilter implements IRateFilter {

    private static final Logger logger = LogManager.getLogger(MovingAverageFilter.class);

    private final double maxJumpPercent;
    private final Map<String, Set<String>> platformRateMap;

    public MovingAverageFilter(double maxJumpPercent, Map<String, Set<String>> platformRateMap) {
        if (maxJumpPercent <= 0)
            throw new IllegalArgumentException("maxDeviationPercent must be positive");

        this.maxJumpPercent = maxJumpPercent;
        this.platformRateMap = platformRateMap;
    }

    @Override
    public boolean shouldAccept(String platform, String rateName, Rate last, Rate candidate, List<Rate> history) {
        if (candidate == null || candidate.getFields() == null) return false;

        // Filtre bu platform/rate için aktif değilse hiçbir işlem yapmadan geç
        if (!shouldFilterApply(platform, rateName)) {
            logger.trace("MovingAverageFilter: Skipped → Platform='{}', Rate='{}'", platform, rateName);
            return true;
        }

        // Minimum 3 veri yoksa kabul et (başlangıç ısınma evresi)
        if (history == null || history.size() < 1) {
            logger.trace("MovingAverageFilter: Not enough history for '{}' - '{}', accepted ({} < 3)",
                    platform, rateName, history == null ? 0 : history.size());
            return true;
        }

        // Ortalamayı al
        double avgBid = history.stream().mapToDouble(r -> r.getFields().getBid()).average().orElse(0.0);
        double avgAsk = history.stream().mapToDouble(r -> r.getFields().getAsk()).average().orElse(0.0);

        double bidDiff = Math.abs(candidate.getFields().getBid() - avgBid);
        double askDiff = Math.abs(candidate.getFields().getAsk() - avgAsk);

        double bidDeviation = bidDiff / avgBid;
        double askDeviation = askDiff / avgAsk;

        boolean bidOk = bidDeviation <= maxJumpPercent;
        boolean askOk = askDeviation <= maxJumpPercent;

        if (!bidOk || !askOk) {
            logger.debug("❌ REJECTED platform={} rate={} → bidDev={:.2f}%% askDev={:.2f}%% (limit={:.2f}%%)",
                    platform, rateName,
                    bidDeviation * 100, askDeviation * 100, maxJumpPercent * 100);
            return false;
        }

        logger.trace("✅ PASSED platform={} rate={} → bidDev={:.2f}%% askDev={:.2f}%% (limit={:.2f}%%)",
                platform, rateName,
                bidDeviation * 100, askDeviation * 100, maxJumpPercent * 100);
        return true;
    }

    private boolean shouldFilterApply(String platform, String rateName) {
        return platformRateMap.containsKey(platform)
                && platformRateMap.get(platform).contains(rateName);
    }
}
