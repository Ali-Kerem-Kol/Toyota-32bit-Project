package com.mydomain.main.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code Rate}, tek bir döviz kuru bilgisini temsil eden bir model sınıfıdır.
 * Bu sınıf, kur adı (`rateName`), alış/satış ve zaman damgası bilgilerini içeren
 * `RateFields` nesnesi ve aktiflik/güncellenme durumunu tutan `RateStatus` nesnesini barındırır.
 * Jackson kütüphanesi ile JSON serileştirme ve deserileştirme işlemlerini destekler.
 *
 * <p>Hizmetin temel işleyişi:
 * <ul>
 *   <li>Veri akışında bir kurun kimliğini (`rateName`) ve detaylarını (`fields`) saklar.</li>
 *   <li>Durum bilgisi (`status`) ile kurun geçerliliğini ve güncellenme durumunu izler.</li>
 *   <li>Deep copy destekli constructor ile nesne kopyalama sağlar.</li>
 * </ul>
 * </p>
 *
 * @author Ali Kerem Kol
 * @version 1.0
 * @since 2025-06-07
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Rate {

    /** Kurun adı */
    private String rateName;

    /** Alış, satış ve zaman damgası bilgilerini içeren nesne */
    private RateFields fields;

    /** Kuranın aktiflik ve güncellenmişlik durumu */
    private RateStatus status;

    /**
     * Boş yapıcı metot.
     * Jackson veya diğer serileştirme kütüphaneleri için gereklidir.
     */
    public Rate() {
    }

    /**
     * JSON verisinden nesne oluşturmak için kullanılan yapıcı metot.
     * Varsayılan olarak `status` aktif ve güncellenmiş olarak ayarlanır.
     *
     * @param rateName  JSON'daki "rateName" alanı, null veya boş olamaz
     * @param bid       JSON'daki "bid" alanı, alış fiyatı
     * @param ask       JSON'daki "ask" alanı, satış fiyatı
     * @param timestamp JSON'daki "timestamp" alanı, zaman damgası (milisaniye)
     * @throws IllegalArgumentException Geçersiz parametreler (null/negatif) durumunda
     */
    public Rate(
            @JsonProperty("rateName") String rateName,
            @JsonProperty("bid") double bid,
            @JsonProperty("ask") double ask,
            @JsonProperty("timestamp") long timestamp) {
        this.rateName = rateName;
        this.fields = new RateFields(bid, ask, timestamp);
        this.status = new RateStatus(true, true);
    }

    /**
     * Tüm alanları elle belirterek Rate nesnesi oluşturur.
     *
     * @param rateName Kuranın adı, null veya boş olamaz
     * @param fields   Alış, satış ve zaman damgası bilgisi, null olamaz
     * @param status   Aktiflik ve güncellenmişlik durumu, null olamaz
     * @throws IllegalArgumentException Geçersiz parametreler (null) durumunda
     */
    public Rate(String rateName, RateFields fields, RateStatus status) {
        this.rateName = rateName;
        this.fields = fields;
        this.status = status;
    }

    // Copy Constructor
    public Rate(Rate other) {
        this.rateName = other.rateName;
        this.fields = new RateFields(other.fields);   // deep copy
        this.status = new RateStatus(other.status);   // deep copy
    }

    /**
     * Kurun adını döner.
     *
     * @return rateName
     */
    public String getRateName() {
        return rateName;
    }

    /**
     * Kurun adını ayarlar.
     *
     * @param rateName Yeni kur adı
     */
    public void setRateName(String rateName) {
        this.rateName = rateName;
    }

    /**
     * RateFields nesnesini döner.
     *
     * @return Alış, satış ve zaman damgası bilgisi
     */
    public RateFields getFields() {
        return fields;
    }

    /**
     * RateFields nesnesini ayarlar.
     *
     * @param fields Yeni alış, satış ve zaman damgası bilgisi
     */
    public void setFields(RateFields fields) {
        this.fields = fields;
    }

    /**
     * RateStatus nesnesini döner.
     *
     * @return Aktiflik ve güncellenmişlik durumu
     */
    public RateStatus getStatus() {
        return status;
    }

    /**
     * RateStatus nesnesini ayarlar.
     *
     * @param status Yeni aktiflik ve güncellenmişlik durumu
     */
    public void setStatus(RateStatus status) {
        this.status = status;
    }

    /**
     * Nesnenin okunabilir dize temsili.
     *
     * @return Rate detaylarını içeren metin
     */
    @Override
    public String toString() {
        return "Rate{" +
                "rateName='" + rateName + '\'' +
                ", fields=" + fields +
                ", status=" + status +
                '}';
    }

}
