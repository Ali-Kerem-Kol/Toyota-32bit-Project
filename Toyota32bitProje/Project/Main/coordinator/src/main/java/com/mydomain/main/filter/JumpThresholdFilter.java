package com.mydomain.main.filter;

import com.mydomain.main.model.Rate;
import com.mydomain.main.model.RateFields;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JumpThresholdFilter:
 * Sadece bir önceki değere göre ani sıçramaları engeller.
 * Sabit bir yüzde eşiği kullanır. Geçmiş gerekmez.
 * Aktif olduğu platform-rate eşleşmeleri config üzerinden gelir.
 */
public class JumpThresholdFilter implements IRateFilter {

    private static final Logger logger = LogManager.getLogger(JumpThresholdFilter.class);

    private final double maxJumpPercent;
    private final Map<String, Set<String>> platformRateMap;

    public JumpThresholdFilter(double maxJumpPercent, Map<String, Set<String>> platformRateMap) {
        if (maxJumpPercent <= 0)
            throw new IllegalArgumentException("maxJumpPercent must be positive");

        this.maxJumpPercent = maxJumpPercent;
        this.platformRateMap = platformRateMap;
    }

    @Override
    public boolean shouldAccept(String platform, String rateName, Rate last, Rate candidate, List<Rate> history) {
        if (candidate == null || candidate.getFields() == null) return false;

        if (!shouldFilterApply(platform, rateName)) {
            logger.trace("JumpThresholdFilter: Skipped → Platform='{}', Rate='{}'", platform, rateName);
            return true;
        }

        if (last == null || last.getFields() == null) {
            logger.trace("JumpThresholdFilter: No previous rate, accepting by default for '{} - {}'", platform, rateName);
            return true;
        }

        RateFields lastFields = last.getFields();
        RateFields candidateFields = candidate.getFields();

        double bidDiff = Math.abs(candidateFields.getBid() - lastFields.getBid());
        double askDiff = Math.abs(candidateFields.getAsk() - lastFields.getAsk());

        double bidJumpPercent = bidDiff / lastFields.getBid();
        double askJumpPercent = askDiff / lastFields.getAsk();

        boolean bidOk = bidJumpPercent <= maxJumpPercent;
        boolean askOk = askJumpPercent <= maxJumpPercent;

        if (!bidOk || !askOk) {
            logger.debug("❌ REJECTED platform={} rate={} → bidJump={:.2f}%% askJump={:.2f}%% (limit={:.2f}%%)",
                    platform, rateName,
                    bidJumpPercent * 100, askJumpPercent * 100, maxJumpPercent * 100);
            return false;
        }

        logger.trace("✅ PASSED platform={} rate={} → bidJump={:.2f}%% askJump={:.2f}%% (limit={:.2f}%%)",
                platform, rateName,
                bidJumpPercent * 100, askJumpPercent * 100, maxJumpPercent * 100);
        return true;
    }

    private boolean shouldFilterApply(String platform, String rateName) {
        return platformRateMap.containsKey(platform)
                && platformRateMap.get(platform).contains(rateName);
    }
}
