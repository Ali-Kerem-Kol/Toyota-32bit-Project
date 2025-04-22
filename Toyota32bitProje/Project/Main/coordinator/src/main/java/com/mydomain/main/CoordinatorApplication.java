package com.mydomain.main;

import com.mydomain.main.config.AppConfig;
import com.mydomain.main.coordinator.ICoordinator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * CoordinatorApplication, bağımsız koordinatör uygulamasının giriş noktasıdır.
 * ConfigReader aracılığıyla yapılandırmayı yükler, koordinatör ve sağlayıcıları başlatır
 * ve uygulamanın çalışmaya devam etmesini sağlar.
 */
public class CoordinatorApplication {

    private static final Logger logger = LogManager.getLogger(CoordinatorApplication.class);

    /**
     * Uygulamayı başlatır:
     * 1) Log ile başlama mesajı yazar,
     * 2) AppConfig ile ICoordinator örneğini oluşturur,
     * 3) Ana iş parçacığını sonsuza dek bekleterek uygulamanın çalışmasını sürdürür.
     *
     * @param args komut satırı argümanları (kullanılmıyor)
     */
    public static void main(String[] args) {
        logger.info("=== Starting Coordinator... ===");

        // Build all: coordinator, providers
        ICoordinator coordinator = AppConfig.init();

        logger.info("=== CoordinatorMain is up and running. ===");

        // Keep running
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            logger.error("Main thread interrupted => shutting down", e);
        }
    }

}
