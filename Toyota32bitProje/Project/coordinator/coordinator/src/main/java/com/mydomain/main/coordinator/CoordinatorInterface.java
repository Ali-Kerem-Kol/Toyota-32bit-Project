package com.mydomain.main.coordinator;

import com.mydomain.main.model.Rate;
import com.mydomain.main.model.RateFields;
import com.mydomain.main.model.RateStatus;
import com.mydomain.main.provider.RESTProvider;
import com.mydomain.main.provider.TCPProvider;

import java.util.Collection;

public interface CoordinatorInterface {
    // Bağlantı gerçekleştiğinde çalışacak callback
    void onConnect(String platformName, Boolean status);

    // Bağlantı koptuğunda çalışacak callback
    void onDisConnect(String platformName, Boolean status);

    // İstenen veri ilk defa geldiğinde
    void onRateAvailable(String platformName, String rateName, Rate rate);

    // İstenen verinin sonraki güncellemeleri
    void onRateUpdate(String platformName, String rateName, RateFields rateFields);

    // İstenen verinin durumu ile ilgili bilgilendirme
    void onRateStatus(String platformName, String rateName, RateStatus rateStatus);

    // 🔹 Yeni Eklenen Metodlar
    RESTProvider getRestProvider(); // REST sağlayıcısını almak için
    TCPProvider getTcpProvider();   // TCP sağlayıcısını almak için

    Collection<Rate> getAllRates();
    Rate getRate(String rateName);

}
