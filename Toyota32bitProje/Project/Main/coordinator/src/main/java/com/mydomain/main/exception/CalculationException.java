package com.mydomain.main.exception;

public class CalculationException extends RuntimeException {
    public CalculationException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
