package com.mydomain.main.http;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HttpService {
    private final RestTemplate restTemplate = new RestTemplate();
    private final String apiKey;

    public HttpService(String apiKey) {
        this.apiKey = apiKey;
    }

    public String get(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Eğer API Key varsa, "Authorization" başlığına ekleyelim
        if (apiKey != null && !apiKey.isEmpty()) {
            headers.set("Authorization", "Bearer " + apiKey);
        }

        HttpEntity<String> entity = new HttpEntity<>(headers);

        /*
        ✅ **YENİ GÜNCELLEME**
        --------------------------------
        - Artık API'den dönen yanıtı **`String`** olarak döndürüyoruz.
        - JSON işlemlerini `RESTProvider` içinde yapacağız.
        */
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        // HTTP Hata Yönetimi
        if (response.getStatusCode() != HttpStatus.OK) {
            throw new ResponseStatusException(response.getStatusCode(), "HTTP Request Failed");
        }

        return response.getBody();
    }
}
