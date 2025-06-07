package com.mydomain.main.provider;

import com.mydomain.main.coordinator.ICoordinator;
import com.mydomain.main.redis.RedisService;

import java.util.Map;

/**
 * {@code IProvider}, veri platformlarına (örneğin, TCP veya REST) bağlanma, bağlantı kesme,
 * abonelik ve abonelikten çıkma işlemleri ile koordinatör referansını ayarlamak için
 * gereken temel metotları tanımlar. Bu arayüz, farklı veri kaynaklarından gelen verileri
 * standardize bir şekilde yönetmek için kullanılır.
 *
 * <p>Hizmetin temel işleyişi:
 * <ul>
 *   <li>Veri platformlarına bağlanmak ve bağlantıyı kesmek için `connect` ve `disConnect` metotları.</li>
 *   <li>Kur (rate) bazında abonelik yönetimi için `subscribe` ve `unSubscribe` metotları.</li>
 *   <li>Koordinatör ve Redis servis entegrasyonu için setter metodları.</li>
 * </ul>
 * </p>
 *
 * <p><b>Özellikler:</b>
 * <ul>
 *   <li>Tüm implementasyonlar thread-safe olmalıdır.</li>
 *   <li>Bağlantı parametreleri opsiyonel olarak Map ile sağlanır.</li>
 * </ul>
 * </p>
 *
 * @author Ali Kerem Kol
 * @version 1.0
 * @since 2025-06-07
 */
public interface IProvider {

    /**
     * Belirtilen platform adına ve parametrelere göre bağlantı kurar.
     * Bağlantı başarısız olursa implementasyonun hata işleme mekanizması devreye girer.
     *
     * @param platformName Bağlanılacak platformun adı (örneğin "TCP_PLATFORM"),
     *                    null veya boş ise implementasyonun davranışı belirsiz
     * @param params Bağlantı için gerekli parametrelerin key-value yapısı,
     *               null olabilir, implementasyonun kullanımına bağlı
     * @throws IllegalArgumentException Geçersiz platformName veya params durumunda
     * @throws RuntimeException Bağlantı sırasında beklenmedik hata oluşursa
     */
    void connect(String platformName, Map<String, String> params);

    /**
     * Belirtilen platform adına ve parametrelere göre bağlantıyı keser.
     * Kaynakların serbest bırakılması implementasyonun sorumluluğundadır.
     *
     * @param platformName Bağlantısı kesilecek platformun adı,
     *                    null veya boş ise implementasyonun davranışı belirsiz
     * @param params Bağlantıyı kesmek için gerekli parametrelerin key-value yapısı,
     *               null olabilir, implementasyonun kullanımına bağlı
     * @throws IllegalArgumentException Geçersiz platformName veya params durumunda
     * @throws RuntimeException Bağlantı kesme sırasında beklenmedik hata oluşursa
     */
    void disConnect(String platformName, Map<String, String> params);

    /**
     * Belirtilen platformda bir kura (rate) abone olur.
     * Abonelik başarısız olursa implementasyonun hata işleme mekanizması devreye girer.
     *
     * @param platformName Abonelik yapılacak platformun adı,
     *                    null veya boş ise implementasyonun davranışı belirsiz
     * @param rateName Abone olunacak kurun adı (örneğin "USDTRY"),
     *                 null veya boş ise implementasyonun davranışı belirsiz
     * @throws IllegalArgumentException Geçersiz platformName veya rateName durumunda
     * @throws RuntimeException Abonelik sırasında beklenmedik hata oluşursa
     */
    void subscribe(String platformName, String rateName);

    /**
     * Belirtilen platformda bir kura (rate) abonelikten çıkar.
     * Abonelik sonlandırma başarısız olursa implementasyonun hata işleme mekanizması devreye girer.
     *
     * @param platformName Abonelikten çıkılacak platformun adı,
     *                    null veya boş ise implementasyonun davranışı belirsiz
     * @param rateName Aboneliği sonlandırılacak kurun adı,
     *                 null veya boş ise implementasyonun davranışı belirsiz
     * @throws IllegalArgumentException Geçersiz platformName veya rateName durumunda
     * @throws RuntimeException Abonelikten çıkma sırasında beklenmedik hata oluşursa
     */
    void unSubscribe(String platformName, String rateName);

    /**
     * Bu sağlayıcının koordinatör arayüzünü ayarlar.
     * Sağlayıcı, veri geldiğinde veya durum değiştiğinde koordinatöre bildirim yapmak için bu referansı kullanır.
     *
     * @param coordinator Uygulamanın koordinatör nesnesi,
     *                    null ise implementasyonun davranışı belirsiz
     * @throws IllegalArgumentException Koordinatör null ise
     */
    void setCoordinator(ICoordinator coordinator);

    /**
     * Bu sağlayıcının Redis servisini ayarlar.
     * Sağlayıcı, verileri önbelleğe almak için bu servisi kullanır.
     *
     * @param redisService Redis operasyonlarını yöneten servis,
     *                    null ise implementasyonun davranışı belirsiz
     * @throws IllegalArgumentException RedisService null ise
     */
    void setRedis(RedisService redisService);


}
