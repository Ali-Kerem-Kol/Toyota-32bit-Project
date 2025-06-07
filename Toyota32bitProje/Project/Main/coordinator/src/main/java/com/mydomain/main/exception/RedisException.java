package com.mydomain.main.exception;

/**
 * {@code RedisException}, Redis operasyonları sırasında (örneğin, veri yazma veya okuma)
 * ortaya çıkan hataları temsil eden bir runtime istisnadır. Bağlantı kesintisi,
 * veri formatı hataları veya zaman aşımı gibi durumlar için kullanılır.
 *
 * <p>Hizmetin temel işleyişi:
 * <ul>
 *   <li>Redis ile iletişimde oluşan teknik hataları kapsar.</li>
 *   <li>Root cause (neden) bilgisi ile birlikte detaylı hata mesajı sunar.</li>
 * </ul>
 * </p>
 *
 * @author Ali Kerem Kol
 * @version 1.0
 * @since 2025-06-07
 */
public class RedisException extends RuntimeException {
    public RedisException(String message, Throwable cause) {
        super(message, cause);
    }
}
