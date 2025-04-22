package com.mydomain.main.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Tek bir döviz kuru bilgisini temsil eder.
 * Bu sınıfın alanları:
 * - rateName: Kuranın adı (örneğin "USDTRY").
 * - fields: Alış (bid), satış (ask) ve zaman damgasını içeren RateFields nesnesi.
 * - status: Kuranın aktiflik ve güncellenmişlik durumunu tutan RateStatus nesnesi.
 * Jackson ile JSON serileştirme ve deserileştirme işlemlerini destekler.
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
     *
     * @param rateName  JSON'daki "rateName" alanı
     * @param bid       JSON'daki "bid" alanı
     * @param ask       JSON'daki "ask" alanı
     * @param timestamp JSON'daki "timestamp" alanı
     */
    public Rate(
            @JsonProperty("rateName") String rateName,
            @JsonProperty("bid") double bid,
            @JsonProperty("ask") double ask,
            @JsonProperty("timestamp") String timestamp) {
        this.rateName = rateName;
        this.fields = new RateFields(bid, ask, timestamp);
        this.status = new RateStatus(true, true);
    }

    /**
     * Tüm alanları elle belirterek Rate nesnesi oluşturur.
     *
     * @param rateName Kuranın adı
     * @param fields   Alış, satış ve zaman damgası bilgisi
     * @param status   Aktiflik ve güncellenmişlik durumu
     */
    public Rate(String rateName, RateFields fields, RateStatus status) {
        this.rateName = rateName;
        this.fields = fields;
        this.status = status;
    }

    /**
     * Kuranın adını döner.
     *
     * @return rateName
     */
    public String getRateName() {
        return rateName;
    }

    /**
     * Kuranın adını ayarlar.
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
