package com.mydomain.main.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * RateStatus sınıfı bir kurun aktiflik ve güncelleme durumunu tutar.
 */
public class RateStatus {

    /** Kurun aktiflik durumu, true ise aktif. */
    private boolean isActive;

    /** Kurun yakın zamanda güncellenip güncellenmediği bilgisi. */
    private boolean isUpdated;

    /**
     * Jackson için varsayılan yapıcı.
     * İsteğe bağlı olarak varsayılan değerler burada atanabilir.
     */
    public RateStatus() {
        // this.isActive = false;
        // this.isUpdated = false;
    }

    /**
     * Kurun aktiflik ve güncelleme durumunu belirterek nesne oluşturur.
     *
     * @param isActive  Kurun aktif olup olmadığını belirtir.
     * @param isUpdated Kurun güncellenmiş olup olmadığını belirtir.
     */
    @JsonCreator
    public RateStatus(
            @JsonProperty("isActive") boolean isActive,
            @JsonProperty("isUpdated") boolean isUpdated) {
        this.isActive = isActive;
        this.isUpdated = isUpdated;
    }

    // Copy Constructor
    public RateStatus(RateStatus other) {
        this.isActive = other.isActive;
        this.isUpdated = other.isUpdated;
    }


    /**
     * Kurun aktiflik durumunu döner.
     *
     * @return true ise kur aktif, false ise pasif.
     */
    public boolean isActive() {
        return isActive;
    }

    /**
     * Kurun aktiflik durumunu ayarlar.
     *
     * @param active true yapıldığında kur aktif olur.
     */
    public void setActive(boolean active) {
        isActive = active;
    }

    /**
     * Kurun güncellenme durumunu döner.
     *
     * @return true ise kur yakın zamanda güncellenmiş.
     */
    public boolean isUpdated() {
        return isUpdated;
    }

    /**
     * Kurun güncellenme durumunu ayarlar.
     *
     * @param updated true yapıldığında kur güncellenmiş kabul edilir.
     */
    public void setUpdated(boolean updated) {
        isUpdated = updated;
    }

    /**
     * RateStatus nesnesinin metin temsili.
     *
     * @return aktiflik ve güncellenme durumlarını içeren metin.
     */
    @Override
    public String toString() {
        return "RateStatus{" +
                "isActive=" + isActive +
                ", isUpdated=" + isUpdated +
                '}';
    }
}
