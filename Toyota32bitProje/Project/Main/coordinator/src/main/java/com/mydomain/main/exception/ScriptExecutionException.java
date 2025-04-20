package com.mydomain.main.exception;

/**
 * JavaScript (Nashorn) veya başka bir dinamik script çalıştırma sırasında
 * oluşan hataları temsil eder.
 */
public class ScriptExecutionException extends RuntimeException {

    public ScriptExecutionException(String message) {
        super(message);
    }

    public ScriptExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
