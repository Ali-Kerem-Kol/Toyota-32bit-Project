package com.mydomain.main.exception;

public class KafkaPublishingException extends RuntimeException {
    private final String payload;          // debugging için

    public KafkaPublishingException(String msg, String payload, Throwable cause) {
        super(msg, cause);
        this.payload = payload;
    }

    public String getPayload() { return payload; }
}
