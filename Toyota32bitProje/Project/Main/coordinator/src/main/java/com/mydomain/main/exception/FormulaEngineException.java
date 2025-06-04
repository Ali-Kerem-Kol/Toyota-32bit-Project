package com.mydomain.main.exception;

public class FormulaEngineException extends Exception {
  public FormulaEngineException(String message) {
    super(message);
  }

  public FormulaEngineException(String message, Throwable cause) {
    super(message, cause);
  }
}
