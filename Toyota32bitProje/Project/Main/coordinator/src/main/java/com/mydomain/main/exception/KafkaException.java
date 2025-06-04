package com.mydomain.main.exception;

public class KafkaException extends RuntimeException {
    private final String payload;          // debugging için

    public KafkaException(String msg, String payload, Throwable cause) {
        super(msg, cause);
        this.payload = payload;
    }

    public String getPayload() { return payload; }
}
