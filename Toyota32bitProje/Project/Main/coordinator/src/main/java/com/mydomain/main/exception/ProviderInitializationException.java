package com.mydomain.main.exception;

public class ProviderInitializationException extends RuntimeException {
    public ProviderInitializationException(String message) {
        super(message);
    }

    public ProviderInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
