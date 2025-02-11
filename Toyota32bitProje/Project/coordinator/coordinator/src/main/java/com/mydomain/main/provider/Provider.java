package com.mydomain.main.provider;

import com.mydomain.main.model.Rate;

import java.util.Map;

public interface Provider {

    // Kullanıcı adı ve şifre gerektiren platformlara bağlanır
    void connect(String platformName, String userId, String password);

    // Kullanıcı adı ve şifre gerektirmeyen platformlara bağlanır (API key vb. parametrelerle)
    void connect(String platformName, Map<String, String> params);

    // Platformdan bağlantıyı keser
    void disconnect(String platformName);

    // Belirtilen rate için abonelik başlatır
    void subscribe(String platformName, String rateName);

    // Aboneliği iptal eder
    void unsubscribe(String platformName, String rateName);

    // REST API için tek seferlik veri çeker
    Rate fetchRate(String platformName, String rateName);
}
