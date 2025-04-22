package com.mydomain.main.exception;

/**
 * Sağlayıcı (provider) başlatılamadığında fırlatılan özel çalışma zamanı istisnası.
 * Örneğin, Reflection ile sağlayıcı sınıfı örneklenemezse veya
 * setCoordinator gibi temel bir yapılandırma adımı başarısız olursa atılır.
 */
public class ProviderInitializationException extends RuntimeException {
    public ProviderInitializationException(String message) {
        super(message);
    }

    public ProviderInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
