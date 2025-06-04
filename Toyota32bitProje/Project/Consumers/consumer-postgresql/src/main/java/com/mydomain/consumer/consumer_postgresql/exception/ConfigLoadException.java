package com.mydomain.consumer.consumer_postgresql.exception;

/**
 * Proje yapılandırma (config.json) ile ilgili yükleme/parsing hataları için kullanılır.
 * Örneğin dosyanın bulunamaması, bozuk JSON, eksik alan vb.
 */
public class ConfigLoadException extends RuntimeException {

    public ConfigLoadException(String message) {
        super(message);
    }

    public ConfigLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
