package com.mydomain.main.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.*;
import java.util.Base64;

/**
 * HttpService: HTTP isteklerini yönetir.
 * Bu versiyonda "maxRetries" kaldırılarak GET isteği tek seferde denenir.
 */
public class HttpService {

    private static final Logger logger = LogManager.getLogger(HttpService.class);

    private final String apiKey;
    private final String basicAuthUser;
    private final String basicAuthPass;

    private final boolean useBearerToken;
    private final boolean useBasicAuth;

    private final Proxy proxy;

    /**
     * maxRetries parametresi kaldırıldı.
     */
    public HttpService(String apiKey, String basicUser, String basicPass,
                       boolean useBearer, boolean useBasic, Proxy proxy) {
        this.apiKey = apiKey;
        this.basicAuthUser = basicUser;
        this.basicAuthPass = basicPass;
        this.useBearerToken = useBearer;
        this.useBasicAuth = useBasic;
        this.proxy = proxy;
    }

    /**
     * Tek deneme – başarısız olursa exception fırlatır.
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

    private HttpURLConnection openConnection(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) (proxy == null ? url.openConnection() : url.openConnection(proxy));
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        if (useBasicAuth && basicAuthUser != null && basicAuthPass != null) {
            String credentials = basicAuthUser + ":" + basicAuthPass;
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
            conn.setRequestProperty("Authorization", "Basic " + encoded);
        }
        if (useBearerToken && apiKey != null) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
        return conn;
    }

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
