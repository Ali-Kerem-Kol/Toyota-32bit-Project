package com.mydomain.main.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.*;
import java.util.Base64;

/**
 * HttpService sınıfı, RESTProvider tarafından kullanılmak üzere
 * HTTP GET isteklerini yönetir. Tek seferlik deneme yapar ve
 * başarısızlık durumunda exception fırlatır.
 */
public class HttpService {

    private static final Logger logger = LogManager.getLogger(HttpService.class);

    private final String APIKEY;
    private final String BASIC_AUTH_USER;
    private final String BASIC_AUTH_PASSWORD;

    private final boolean USE_BEARER_TOKEN;
    private final boolean USE_BASIC_AUTH;

    private final Proxy PROXY;

    public HttpService(String apiKey, String basicUser, String basicPass, boolean useBearer, boolean useBasic, Proxy proxy) {
        this.APIKEY = apiKey;
        this.BASIC_AUTH_USER = basicUser;
        this.BASIC_AUTH_PASSWORD = basicPass;
        this.USE_BEARER_TOKEN = useBearer;
        this.USE_BASIC_AUTH = useBasic;
        this.PROXY = proxy;
    }


    /**
     * Belirtilen URL'e tek seferlik GET isteği gönderir.
     * HTTP durum kodu 2xx aralığında değilse RuntimeException fırlatır.
     *
     * @param urlStr İstek yapılacak tam URL
     * @return Sunucudan dönen yanıt gövdesi metin olarak
     * @throws Exception İstek sırasında bağlantı veya okuma hatası oluşursa
     */
    public String get(String urlStr) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = openConnection(urlStr);
            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                return readResponse(conn.getInputStream());
            } else {
                logger.warn("HTTP GET error => code: {}", responseCode);
                throw new RuntimeException("HTTP GET error => code: " + responseCode);
            }
        } catch (Exception e) {
            logger.error("HTTP GET request failed => {}", e.getMessage());
            logger.debug("Stacktrace:", e);
            throw e;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Yeni bir HttpURLConnection açar, zaman aşımı ve yetkilendirme başlıklarını ayarlar.
     *
     * @param urlStr Bağlanılacak URL
     * @return Açılmış ve yapılandırılmış HttpURLConnection nesnesi
     * @throws Exception URL oluşturma veya bağlantı açma sırasında hata oluşursa
     */
    private HttpURLConnection openConnection(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) (PROXY == null
                ? url.openConnection()
                : url.openConnection(PROXY));
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        if (USE_BASIC_AUTH && BASIC_AUTH_USER != null && BASIC_AUTH_PASSWORD != null) {
            String credentials = BASIC_AUTH_USER + ":" + BASIC_AUTH_PASSWORD;
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
            conn.setRequestProperty("Authorization", "Basic " + encoded);
        }
        if (USE_BEARER_TOKEN && APIKEY != null) {
            conn.setRequestProperty("Authorization", "Bearer " + APIKEY);
        }
        return conn;
    }

    /**
     * Açık InputStream'den satır satır okuyarak tüm içeriği String olarak döner.
     *
     * @param is Sunucudan gelen InputStream
     * @return Okunan metin içeriği
     * @throws Exception Okuma sırasında hata oluşursa
     */
    private String readResponse(InputStream is) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

}
