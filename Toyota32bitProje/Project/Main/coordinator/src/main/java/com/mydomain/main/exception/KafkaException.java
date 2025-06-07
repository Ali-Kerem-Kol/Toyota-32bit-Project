package com.mydomain.main.exception;

/**
 * {@code KafkaException}, Kafka producer veya consumer işlemleri sırasında
 * ortaya çıkan hataları temsil eden bir runtime istisnadır. Gönderim başarısızlığı,
 * bağlantı kesintisi veya geçersiz payload gibi durumlar için kullanılır.
 *
 * <p>Hizmetin temel işleyişi:
 * <ul>
 *   <li>Hata mesajı ve opsiyonel payload bilgisi ile hata ayıklama sağlar.</li>
 *   <li>Root cause (neden) bilgisi ile birlikte detaylı hata yönetimi sunar.</li>
 * </ul>
 * </p>
 *
 * @author Ali Kerem Kol
 * @version 1.0
 * @since 2025-06-07
 */
public class KafkaException extends RuntimeException {
    private final String payload;          // debugging için

    public KafkaException(String msg, String payload, Throwable cause) {
        super(msg, cause);
        this.payload = payload;
    }

    public String getPayload() { return payload; }
}
