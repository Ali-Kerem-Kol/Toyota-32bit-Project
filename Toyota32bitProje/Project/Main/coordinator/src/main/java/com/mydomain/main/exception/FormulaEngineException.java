package com.mydomain.main.exception;

/**
 * {@code FormulaEngineException}, formül motoru (örneğin, JavaScript tabanlı hesaplama motoru)
 * ile ilgili hataları temsil eden bir istisnadır. Geçersiz formül syntax’i, çalışma zamanı
 * hataları veya dosya yükleme sorunları gibi durumlar için kullanılır.
 *
 * <p>Hizmetin temel işleyişi:
 * <ul>
 *   <li>Formül motorunun çalıştırılamaması veya geçersiz formül nedeniyle oluşan hataları kapsar.</li>
 *   <li>Root cause (neden) bilgisi ile birlikte detaylı hata mesajı sunar.</li>
 * </ul>
 * </p>
 *
 * @author Ali Kerem Kol
 * @version 1.0
 * @since 2025-06-07
 */
public class FormulaEngineException extends Exception {
  public FormulaEngineException(String message) {
    super(message);
  }

  public FormulaEngineException(String message, Throwable cause) {
    super(message, cause);
  }
}
