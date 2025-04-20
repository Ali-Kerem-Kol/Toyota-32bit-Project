package com.mydomain.consumer_elasticsearch;

import com.mydomain.consumer_elasticsearch.config.AppConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ConsumerElasticsearchApplication is the entry point for the consumer-elasticsearch microservice.
 */
public class ConsumerElasticsearchApplication {

	private static final Logger logger = LogManager.getLogger(ConsumerElasticsearchApplication.class);

	public static void main(String[] args) {
		logger.info("=== Starting consumer-elasticsearch application... ===");

		// 1) Servisleri AppConfig'te init ediyoruz
		AppConfig.init();

		// 2) Shutdown hook ekleyelim (SIGTERM vs. geldiğinde kapatalım)
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			logger.info("Shutdown Hook triggered => calling AppConfig.shutdown()");
			AppConfig.shutdown();
		}));

		logger.info("=== consumer-elasticsearch is up and running. ===");

		// 3) Ana thread'i bekletelim (Uygulama süresiz çalışsın)
		try {
			Thread.currentThread().join(); // Burada bloklanır, "docker stop" veya kill gelene kadar
		} catch (InterruptedException e) {
			logger.error("Main thread interrupted => shutting down.", e);
			AppConfig.shutdown();
		}
	}
}
