package com.mydomain.main.provider;

import com.mydomain.main.cache.RateCache;
import com.mydomain.main.coordinator.ICoordinator;
import java.util.Map;

/**
 * Sağlayıcı (Provider) arabirimi, veri platformlarına bağlanma,
 * bağlantı kesme, abonelik ve abonelikten çıkma işlemleri ile
 * koordinatör referansını ayarlamak için gereken temel metotları tanımlar.
 */
public interface IProvider {

    /**
     * Belirtilen platform adına ve parametrelere göre bağlantı kurar.
     *
     * @param platformName Bağlanılacak platformun adı (örneğin "TCP_PLATFORM")
     * @param params       Bağlantı için gerekli parametrelerin key-value yapısı
     */
    void connect(String platformName, Map<String, String> params);

    /**
     * Belirtilen platform adına ve parametrelere göre bağlantıyı keser.
     *
     * @param platformName Bağlantısı kesilecek platformun adı
     * @param params       Bağlantıyı kesmek için gerekli parametrelerin key-value yapısı
     */
    void disConnect(String platformName, Map<String, String> params);

    /**
     * Belirtilen platformda bir kura (rate) abone olur.
     *
     * @param platformName Abonelik yapılacak platformun adı
     * @param rateName     Abone olunacak kurun adı (örneğin "PF1_USDTRY")
     */
    void subscribe(String platformName, String rateName);

    /**
     * Belirtilen platformda bir kura (rate) abonelikten çıkar.
     *
     * @param platformName Abonelikten çıkılacak platformun adı
     * @param rateName     Aboneliği sonlandırılacak kurun adı
     */
    void unSubscribe(String platformName, String rateName);

    /**
     * Bu sağlayıcının koordinatör arayüzünü alır.
     * Sağlayıcı, veri geldiğinde veya durum değiştiğinde koordinatöre bildirim yapar.
     *
     * @param coordinator Uygulamanın koordinatör nesnesi
     */
    void setCoordinator(ICoordinator coordinator);

    void setCache(RateCache cache); // Sağlayıcıya RateCache nesnesi eklenir, böylece sağlayıcı verileri önbelleğe alabilir.

}
