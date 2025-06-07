package com.mydomain.main.coordinator;

import com.mydomain.main.model.Rate;
import com.mydomain.main.model.RateFields;
import com.mydomain.main.model.RateStatus;

/**
 * {@code ICoordinator}, veri sağlayıcılarından gelen olayları dinleyen ve işleyen arayüzdür.
 * Bağlantı olayları, yeni veri gelişleri, güncellemeler ve durum değişiklikleri için callback
 * metotları içerir. Ayrıca, veri akışını koordine etmek ve diğer servislerle entegrasyon
 * sağlamak için tasarlanmıştır.
 *
 * <p>Hizmetin temel işleyişi:
 * <ul>
 *   <li>Sağlayıcıların bağlantı durumlarını (`onConnect`, `onDisConnect`) izler.</li>
 *   <li>Yeni rate verilerini (`onRateAvailable`) ve güncellemeleri (`onRateUpdate`) işler.</li>
 *   <li>Rate durum değişikliklerini (`onRateStatus`) takip eder.</li>
 * </ul>
 * </p>
 *
 * <p><b>Özellikler:</b>
 * <ul>
 *   <li>Esnek ve modüler bir callback yapısı sunar.</li>
 *   <li>Implementasyonlar thread-safe olmalıdır.</li>
 * </ul>
 * </p>
 *
 * @author Ali Kerem Kol
 * @version 1.0
 * @since 2025-06-07
 */
public interface ICoordinator {

    /**
     * Sağlayıcı ile bağlantı kurulduğunda çağrılır.
     * Bu metot, bağlantı durumunu loglamak veya diğer işlemler için kullanılabilir.
     *
     * @param platformName Sağlayıcı platformunun adı (örneğin "TCP_PLATFORM"),
     *                    null veya boş ise implementasyonun davranışı belirsiz
     * @param status Bağlantı durumu (true = başarılı, false = başarısız),
     *               null ise implementasyonun davranışı belirsiz
     */
    void onConnect(String platformName, Boolean status);

    /**
     * Sağlayıcı ile bağlantı kesildiğinde çağrılır.
     * Bu metot, bağlantı kesilme durumunu işlemek için kullanılabilir.
     *
     * @param platformName Sağlayıcı platformunun adı,
     *                    null veya boş ise implementasyonun davranışı belirsiz
     * @param status Çıkış işleminin başarılı olup olmadığı (true/false),
     *               null ise implementasyonun davranışı belirsiz
     */
    void onDisConnect(String platformName, Boolean status);

    /**
     * Yeni bir oran verisi ilk kez geldiğinde çağrılır.
     * Bu metot, yeni veriyi işlemek veya önbelleğe almak için kullanılabilir.
     *
     * @param platformName Sağlayıcı platformunun adı,
     *                    null veya boş ise implementasyonun davranışı belirsiz
     * @param rateName Gelen oranın anahtarı (örneğin "USDTRY"),
     *                 null veya boş ise implementasyonun davranışı belirsiz
     * @param rate Gelen Rate nesnesi,
     *             null ise implementasyonun davranışı belirsiz
     */
    void onRateAvailable(String platformName, String rateName, Rate rate);

    /**
     * Mevcut bir oran güncellendiğinde çağrılır.
     * Bu metot, güncellenen veriyi işlemek veya yayınlamak için kullanılabilir.
     *
     * @param platformName Sağlayıcı platformunun adı,
     *                    null veya boş ise implementasyonun davranışı belirsiz
     * @param rateName Güncellenen oranın anahtarı,
     *                 null veya boş ise implementasyonun davranışı belirsiz
     * @param rateFields Yeni değerleri içeren RateFields nesnesi,
     *                   null ise implementasyonun davranışı belirsiz
     */
    void onRateUpdate(String platformName, String rateName, RateFields rateFields);

    /**
     * Oran durumunda (aktif/pasif) değişiklik olduğunda çağrılır.
     * Bu metot, durum değişikliğini izlemek veya güncellemek için kullanılabilir.
     *
     * @param platformName Sağlayıcı platformunun adı,
     *                    null veya boş ise implementasyonun davranışı belirsiz
     * @param rateName Durumu değişen oranın anahtarı,
     *                 null veya boş ise implementasyonun davranışı belirsiz
     * @param rateStatus Yeni RateStatus nesnesi,
     *                   null ise implementasyonun davranışı belirsiz
     */
    void onRateStatus(String platformName, String rateName, RateStatus rateStatus);

}
