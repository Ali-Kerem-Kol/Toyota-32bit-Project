package com.mydomain.main.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code RateFields}, bir döviz kuru için sayısal alanları (alış fiyatı, satış fiyatı
 * ve zaman damgası) saklayan bir model sınıfıdır. Bu sınıf, `Rate` nesnesi içinde
 * kurun detay bilgilerini temsil eder ve Jackson ile JSON serileştirme/deserileştirme
 * işlemlerini destekler.
 *
 * <p>Hizmetin temel işleyişi:
 * <ul>
 *   <li>Alış (`bid`) ve satış (`ask`) fiyatlarını milisaniye cinsinden zaman damgası ile birlikte saklar.</li>
 *   <li>Deep copy destekli constructor ile nesne kopyalama sağlar.</li>
 * </ul>
 * </p>
 *
 * @author Ali Kerem Kol
 * @version 1.0
 * @since 2025-06-07
 */
public class RateFields {

    /** Alış (bid) fiyatı */
    private double bid;

    /** Satış (ask) fiyatı */
    private double ask;

    /** Zaman damgası, ISO formatlı metin */
    private long timestamp;

    /**
     * JSON verisinden deseralize etmek için kullanılan yapıcı metot.
     *
     * @param bid       Alış fiyatı (JSON'daki "bid" alanı)
     * @param ask       Satış fiyatı (JSON'daki "ask" alanı)
     * @param timestamp Zaman damgası (JSON'daki "timestamp" alanı)
     */
    public RateFields(
            @JsonProperty("bid") double bid,
            @JsonProperty("ask") double ask,
            @JsonProperty("timestamp") long timestamp) {
        this.bid = bid;
        this.ask = ask;
        this.timestamp = timestamp;
    }

    // Copy Constructor
    public RateFields(RateFields other) {
        this.bid = other.bid;
        this.ask = other.ask;
        this.timestamp = other.timestamp;
    }

    /**
     * Alış fiyatını döner.
     *
     * @return bid
     */
    public double getBid() {
        return bid;
    }

    /**
     * Alış fiyatını ayarlar.
     *
     * @param bid Yeni alış fiyatı
     */
    public void setBid(double bid) {
        this.bid = bid;
    }

    /**
     * Satış fiyatını döner.
     *
     * @return ask
     */
    public double getAsk() {
        return ask;
    }

    /**
     * Satış fiyatını ayarlar.
     *
     * @param ask Yeni satış fiyatı
     */
    public void setAsk(double ask) {
        this.ask = ask;
    }

    /**
     * Zaman damgasını döner.
     *
     * @return timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Zaman damgasını ayarlar.
     *
     * @param timestamp Yeni zaman damgası
     */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Nesnenin metin temsili.
     *
     * @return bid, ask ve timestamp bilgilerini içeren metin
     */
    @Override
    public String toString() {
        return "RateFields{" +
                "bid=" + bid +
                ", ask=" + ask +
                ", timestamp='" + timestamp + '\'' +
                '}';
    }
}
