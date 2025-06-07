package com.mydomain.main.filter;

import com.mydomain.main.model.Rate;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tüm filtrelerin uygulayacağı ortak arayüz.
 * Platform ve rateName bilgisiyle birlikte çalışır.
 * Filtreler, veri akışını platform-rate bazında kontrol etmek için kullanılır.
 *
 * <p>Implementasyonlar:
 * <ul>
 *   <li>Filtrelerin platform-rate eşleşmeleri `FilterService` tarafından enjekte edilir.</li>
 *   <li>`shouldAccept` metodu, verinin kabul edilip edilmeyeceğine karar verir.</li>
 * </ul>
 * </p>
 *
 * @author Ali Kerem Kol
 * @version 1.0
 * @since 2025-06-07
 */
public interface IRateFilter {

    /**
     * Belirli bir veri için filtre kararını verir.
     * Platform ve rateName bazında verinin kabul edilip edilmeyeceğini kontrol eder.
     *
     * @param platformName Verinin geldiği platform adı (örnek: "TCP_PLATFORM"),
     *                    null veya boş ise implementasyonun davranışı belirsiz
     * @param rateName Döviz kuru adı (örnek: "USDTRY"),
     *                 null veya boş ise implementasyonun davranışı belirsiz
     * @param last Cache'teki son kabul edilen veri, null olabilir
     * @param candidate Yeni gelen ve değerlendirilecek veri,
     *                  null ise genellikle false döndürülür
     * @param history Platform + rate'e ait geçmiş veri listesi,
     *                null olabilir, implementasyonun kullanımına bağlı
     * @return Eğer kabul edilecekse true, reddedilecekse false
     */
    boolean shouldAccept(String platformName, String rateName, Rate last, Rate candidate, List<Rate> history);

    /**
     * FilterService'in zorunlu olarak çağıracağı ayar metodudur.
     * Filtrenin hangi platform ve rate’ler için uygulanacağını tanımlar.
     *
     * @param assignment Platformlara göre rate’lerin eşlendiği Map nesnesi,
     *                   null ise implementasyonun davranışı belirsiz
     */
    void setPlatformAssignments(Map<String, Set<String>> assignment);

}
