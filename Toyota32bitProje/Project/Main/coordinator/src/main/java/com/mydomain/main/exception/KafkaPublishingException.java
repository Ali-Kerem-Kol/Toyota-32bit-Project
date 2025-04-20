package com.mydomain.main.exception;

/**
 * Kafka'ya mesaj gönderilirken yaşanan hatalar (bağlantı, timeout, serialize vb.)
 * bu sınıf ile yakalanıp fırlatılabilir.
 */
public class KafkaPublishingException extends RuntimeException {

    public KafkaPublishingException(String message) {
        super(message);
    }

    public KafkaPublishingException(String message, Throwable cause) {
        super(message, cause);
    }
}
