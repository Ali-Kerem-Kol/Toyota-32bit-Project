package com.mydomain.main.exception;

/**
 * {@code CalculationException}, rate hesaplama işlemlerinde (örneğin, `RateCalculatorService` tarafından)
 * ortaya çıkan beklenmedik hataları temsil eden bir runtime istisnadır. Bu istisna, hesaplama
 * mantığındaki hatalar (örneğin, geçersiz formül veya veri) için kullanılır.
 *
 * <p>Hizmetin temel işleyişi:
 * <ul>
 *   <li>Hesaplama sırasında oluşan teknik hataları (örneğin, bölme sıfıra) kapsar.</li>
 *   <li>Root cause (neden) bilgisi ile birlikte detaylı hata mesajı sunar.</li>
 * </ul>
 * </p>
 *
 * @author Ali Kerem Kol
 * @version 1.0
 * @since 2025-06-07
 */
public class CalculationException extends RuntimeException {
    public CalculationException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
