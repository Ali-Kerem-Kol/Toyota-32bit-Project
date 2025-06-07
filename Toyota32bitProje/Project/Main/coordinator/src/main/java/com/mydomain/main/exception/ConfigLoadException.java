package com.mydomain.main.exception;

/**
 * {@code ConfigLoadException}, proje yapılandırma dosyası (`config.json`) ile ilgili
 * yükleme veya ayrıştırma (parsing) hatalarını temsil eden bir runtime istisnadır.
 * Dosyanın bulunamaması, bozuk JSON yapısı veya eksik zorunlu alanlar gibi durumlar için kullanılır.
 *
 * <p>Hizmetin temel işleyişi:
 * <ul>
 *   <li>Yapılandırma dosyasının okunamaması veya geçersiz olması durumlarını kapsar.</li>
 *   <li>Root cause (neden) bilgisi ile birlikte detaylı hata mesajı sunar.</li>
 * </ul>
 * </p>
 *
 * @author Ali Kerem Kol
 * @version 1.0
 * @since 2025-06-07
 */
public class ConfigLoadException extends RuntimeException {

    public ConfigLoadException(String message) {
        super(message);
    }

    public ConfigLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
