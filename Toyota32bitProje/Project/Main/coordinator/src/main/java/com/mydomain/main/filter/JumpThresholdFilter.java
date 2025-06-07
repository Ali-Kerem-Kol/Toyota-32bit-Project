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
 * {@code JumpThresholdFilter}, bir önceki değere göre ani sıçramaları engelleyen bir filtredir.
 * Sabit bir yüzde eşiği (maxJumpPercent) kullanarak bid ve ask değerlerindeki ani değişiklikleri
 * reddeder. Geçmiş veri listesine (history) ihtiyaç duymaz.
 * Aktif olduğu platform-rate eşleşmeleri `FilterService` tarafından konfigürasyondan yüklenir.
 *
 * <p>Hizmetin temel işleyişi:
 * <ul>
 *   <li>`jumpThresholdFilter.json` dosyasından maxJumpPercent parametresi okunur.</li>
 *   <li>Platform-rate eşleşmeleri `setPlatformAssignments` ile enjekte edilir.</li>
 *   <li>`shouldAccept`, bid ve ask değerlerindeki sıçramaları yüzde olarak kontrol eder.</li>
 * </ul>
 * </p>
 *
 * <p><b>Özellikler:</b>
 * <ul>
 *   <li>Ani sıçramaları (örneğin, %5’ten fazla) reddeder.</li>
 *   <li>Loglama için Apache Log4j ile detaylı izleme sağlar.</li>
 *   <li>Geçmiş veriye bağımlı değildir, sadece son veri ile çalışır.</li>
 * </ul>
 * </p>
 *
 * @author Ali Kerem Kol
 * @version 1.0
 * @since 2025-06-07
 */
public class JumpThresholdFilter implements IRateFilter {

    private static final Logger log = LogManager.getLogger(JumpThresholdFilter.class);
    private static final String CONFIG_FILE_PATH = "/app/Main/coordinator/config/jumpThresholdFilter.json";

    private final double maxJumpPercent;
    private Map<String, Set<String>> platformRateMap;

    /**
     * ZORUNLU: no-arg kurucu metod.
     * `FilterService` tarafından reflection ile çağrılır ve `jumpThresholdFilter.json`
     * dosyasından maxJumpPercent parametresini yükler.
     *
     * @throws IllegalStateException Konfigürasyon dosyası yüklenemezse
     */
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

    /**
     * FilterService bu setter ile platform-rate eşleşmesini enjekte eder.
     * Filtrenin hangi platform ve rate’ler için uygulanacağını tanımlar.
     *
     * @param m Platformlara göre rate’lerin eşlendiği Map nesnesi,
     *          null ise implementasyonun davranışı belirsiz
     */
    @Override
    public void setPlatformAssignments(Map<String, Set<String>> m) {
        this.platformRateMap = m;
    }

    /**
     * Yeni gelen verinin (candidate) bir önceki veriye (last) göre ani sıçrama
     * içerip içermediğini kontrol eder. Sıçrama, maxJumpPercent eşiğini aşarsa reddeder.
     *
     * @param platform Verinin geldiği platform adı (örnek: "TCP_PLATFORM"),
     *                 null veya boş ise true döndürülür (filtre uygulanmaz)
     * @param rateName Döviz kuru adı (örnek: "USDTRY"),
     *                 null veya boş ise true döndürülür (filtre uygulanmaz)
     * @param last Cache'teki son kabul edilen veri, null ise true döndürülür
     * @param candidate Yeni gelen ve değerlendirilecek veri,
     *                  null ise false döndürülür
     * @param history Platform + rate'e ait geçmiş veri listesi,
     *                bu filtrede kullanılmaz, null olabilir
     * @return Eğer sıçrama eşiği aşılmadıysa true, aksi halde false
     */
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

    /**
     * Filtrenin belirtilen platform ve rate için uygulanması gerektiğini kontrol eder.
     *
     * @param platform Platform adı
     * @param rateName Rate adı
     * @return Eğer filtre bu platform-rate çifti için uygulanacaksa true, aksi halde false
     */
    private boolean shouldFilterApply(String platform, String rateName) {
        return platformRateMap.containsKey(platform)
                && platformRateMap.get(platform).contains(rateName);
    }
}
