package com.mydomain.main;

import com.mydomain.main.config.AppConfig;
import com.mydomain.main.coordinator.ICoordinator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>CoordinatorMain is the entry point for the standalone coordinator application.
 * It initializes the Coordinator + Providers from config.json, then waits indefinitely.</p>
 */
public class CoordinatorApplication {

    private static final Logger logger = LogManager.getLogger(CoordinatorApplication.class);

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
