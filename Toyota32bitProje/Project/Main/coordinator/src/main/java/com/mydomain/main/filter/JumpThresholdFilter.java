package com.mydomain.main.filter;

import com.mydomain.main.model.Rate;
import com.mydomain.main.model.RateFields;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private static final Logger log = LogManager.getLogger(JumpThresholdFilter.class);
    private static final String CONFIG_FILE_PATH = "/app/Main/coordinator/config/jumpThresholdFilter.json";

    private final double maxJumpPercent;
    private Map<String, Set<String>> platformRateMap;

    /** ZORUNLU: no-arg kurucu (FilterService reflection ile böyle oluşturacak) */
    public JumpThresholdFilter() {
        // 1) JSON’u oku
        JSONObject params;
        try (InputStream in = Files.newInputStream(Path.of(CONFIG_FILE_PATH))) {
            params = new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load " + CONFIG_FILE_PATH, e);
        }
        // 2) Parametreleri al
        this.maxJumpPercent = params.getDouble("maxJumpPercent");
        log.info("JumpThresholdFilter params loaded: maxJumpPercent={}", maxJumpPercent);
    }

    /** FilterService bu setter ile platform-rate eşleşmesini enjekte edecek */
    @Override
    public void setPlatformAssignments(Map<String, Set<String>> m) {
        this.platformRateMap = m;
    }

    @Override
    public boolean shouldAccept(String platform, String rateName, Rate last, Rate candidate, List<Rate> history) {
        if (candidate == null || candidate.getFields() == null) return false;

        if (!shouldFilterApply(platform, rateName)) {
            log.trace("JumpThresholdFilter: Skipped → Platform='{}', Rate='{}'", platform, rateName);
            return true;
        }

        if (last == null || last.getFields() == null) {
            log.trace("JumpThresholdFilter: No previous rate, accepting by default for '{} - {}'", platform, rateName);
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
            log.debug("❌ REJECTED platform={} rate={} → bidJump={:.2f}%% askJump={:.2f}%% (limit={:.2f}%%)",
                    platform, rateName,
                    bidJumpPercent * 100, askJumpPercent * 100, maxJumpPercent * 100);
            return false;
        }

        log.debug("✅ PASSED platform={} rate={} → bidJump={:.2f}%% askJump={:.2f}%% (limit={:.2f}%%)",
                platform, rateName,
                bidJumpPercent * 100, askJumpPercent * 100, maxJumpPercent * 100);
        return true;
    }

    private boolean shouldFilterApply(String platform, String rateName) {
        return platformRateMap.containsKey(platform)
                && platformRateMap.get(platform).contains(rateName);
    }
}
