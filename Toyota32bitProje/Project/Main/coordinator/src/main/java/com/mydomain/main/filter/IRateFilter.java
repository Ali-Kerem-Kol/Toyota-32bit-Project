package com.mydomain.main.filter;

import com.mydomain.main.model.Rate;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tüm filtrelerin uygulayacağı ortak arayüz.
 * Artık platform ve rateName bilgisiyle birlikte çalışır.
 */
public interface IRateFilter {
    /**
     * Belirli bir veri için filtre kararını verir.
     *
     * @param platformName   Verinin geldiği platform adı (örnek: "TCP_PLATFORM")
     * @param rateName   Döviz kuru adı (örnek: "USDTRY")
     * @param last       Cache'teki son kabul edilen veri
     * @param candidate  Yeni gelen ve değerlendirilecek veri
     * @param history    Platform + rate'e ait geçmiş veri listesi
     * @return Eğer kabul edilecekse true, reddedilecekse false
     */
    boolean shouldAccept(String platformName, String rateName, Rate last, Rate candidate, List<Rate> history);

    /** FilterService'in zorunlu olarak çağıracağı ayar */
    void setPlatformAssignments(Map<String, Set<String>> assignment);

}
