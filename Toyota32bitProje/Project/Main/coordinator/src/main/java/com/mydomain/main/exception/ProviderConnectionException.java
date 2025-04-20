package com.mydomain.main.exception;

/**
 * TCPProvider veya RESTProvider gibi bağlantı kuran sınıflarda
 * bağlantı kurulamadığında, veri alınamadığında veya yeniden bağlanma
 * başarısız olduğunda fırlatılabilir.
 */
public class ProviderConnectionException extends RuntimeException {

    public ProviderConnectionException(String message) {
        super(message);
    }

    public ProviderConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
