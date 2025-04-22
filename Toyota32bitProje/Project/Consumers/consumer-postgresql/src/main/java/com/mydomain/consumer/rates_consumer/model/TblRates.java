package com.mydomain.consumer.rates_consumer.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * TblRates sınıfı, veritabanındaki tbl_rates tablosuna karşılık gelen JPA entity'sidir.
 * Bu sınıf, tüketici uygulaması tarafından alınan döviz kuru mesajlarının
 * veritabanına kaydedilmesi amacıyla kullanılır.
 */
@Entity
@Table(name = "tbl_rates")
public class TblRates {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String rateName;
    private double bid;
    private double ask;
    private LocalDateTime rateUpdateTime;
    private LocalDateTime dbUpdateTime;

    public Long getId() { return id; }

    public String getRateName() { return rateName; }
    public void setRateName(String rateName) { this.rateName = rateName; }

    public double getBid() { return bid; }
    public void setBid(double bid) { this.bid = bid; }

    public double getAsk() { return ask; }
    public void setAsk(double ask) { this.ask = ask; }

    public LocalDateTime getRateUpdateTime() { return rateUpdateTime; }
    public void setRateUpdateTime(LocalDateTime rateUpdateTime) { this.rateUpdateTime = rateUpdateTime; }

    public LocalDateTime getDbUpdateTime() { return dbUpdateTime; }
    public void setDbUpdateTime(LocalDateTime dbUpdateTime) { this.dbUpdateTime = dbUpdateTime; }
}
