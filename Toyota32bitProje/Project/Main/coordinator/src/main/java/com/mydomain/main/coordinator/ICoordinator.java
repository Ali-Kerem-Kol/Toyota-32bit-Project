package com.mydomain.main.coordinator;

import com.mydomain.main.model.Rate;
import com.mydomain.main.model.RateFields;
import com.mydomain.main.model.RateStatus;

/**
 * Veri sağlayıcılarından gelen olayları dinleyen ve işleyen arayüz.
 * <p>
 * Bağlantı olayları, yeni veri gelişleri, güncellemeler ve durum değişiklikleri
 * için callback metotları içerir. Ayrıca isteğe bağlı olarak REST üzerinden
 * oran çekme metodu da tanımlıdır.
 * </p>
 */
public interface ICoordinator {

    /**
     * Sağlayıcı ile bağlantı kurulduğunda çağrılır.
     *
     * @param platformName Sağlayıcı platformunun adı (ör. "TCP_PLATFORM")
     * @param status       Bağlantı durumu (true = bağlantı başarılı, false = başarısız)
     */
    void onConnect(String platformName, Boolean status);

    /**
     * Sağlayıcı ile bağlantı kesildiğinde çağrılır.
     *
     * @param platformName Sağlayıcı platformunun adı
     * @param status       Çıkış işleminin başarılı olup olmadığı (true/false)
     */
    void onDisConnect(String platformName, Boolean status);

    /**
     * Yeni bir oran verisi ilk kez geldiğinde çağrılır.
     *
     * @param platformName Sağlayıcı platformunun adı
     * @param rateName     Gelen oranın anahtarı (ör. "PF1_USDTRY")
     * @param rate         Gelen Rate nesnesi
     */
    void onRateAvailable(String platformName, String rateName, Rate rate);

    /**
     * Mevcut bir oran güncellendiğinde çağrılır.
     *
     * @param platformName Sağlayıcı platformunun adı
     * @param rateName     Güncellenen oranın anahtarı
     * @param rateFields   Yeni değerleri içeren RateFields nesnesi
     */
    void onRateUpdate(String platformName, String rateName, RateFields rateFields);

    /**
     * Oran durumunda (aktif/pasif) değişiklik olduğunda çağrılır.
     *
     * @param platformName Sağlayıcı platformunun adı
     * @param rateName     Durumu değişen oranın anahtarı
     * @param rateStatus   Yeni RateStatus nesnesi
     */
    void onRateStatus(String platformName, String rateName, RateStatus rateStatus);

}
