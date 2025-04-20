package com.mydomain.consumer_elasticsearch.config;

import com.mydomain.consumer_elasticsearch.service.ElasticsearchService;
import com.mydomain.consumer_elasticsearch.service.KafkaConsumerService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * AppConfig is responsible for initializing the Kafka consumer,
 * Elasticsearch client, and launching the consumer thread.
 */
public class AppConfig {

    private static final Logger logger = LogManager.getLogger(AppConfig.class);

    private static KafkaConsumerService kafkaConsumerService;
    private static ElasticsearchService esService;
    private static Thread consumerThread;

    /**
     * init() metodu, config.json'dan okunan parametrelerle
     * ElasticsearchService ve KafkaConsumerService'i kurup başlatır.
     */
    public static void init() {
        logger.info("=== AppConfig init started ===");

        // 1) ElasticsearchService oluştur
        String esHost = ConfigReader.getEsHost();
        int esPort = ConfigReader.getEsPort();
        String esIndex = ConfigReader.getEsIndexName();
        esService = new ElasticsearchService(esHost, esPort, esIndex);
        logger.info("ElasticsearchService created => {}:{}", esHost, esPort);

        // 2) KafkaConsumerService oluştur
        String kafkaBootstrap = ConfigReader.getKafkaBootstrapServers();
        String groupId = ConfigReader.getKafkaGroupId();
        String topic = ConfigReader.getKafkaTopic();

        kafkaConsumerService = new KafkaConsumerService(kafkaBootstrap, groupId, topic, esService);
        logger.info("KafkaConsumerService created => bootstrap={}, groupId={}, topic={}",
                kafkaBootstrap, groupId, topic);

        // 3) Consumer'ı ayrı bir thread'de başlat
        consumerThread = new Thread(() -> {
            logger.info("Consumer thread started...");
            kafkaConsumerService.start();
        }, "KafkaConsumerThread");
        consumerThread.start();

        logger.info("=== AppConfig init completed ===");
    }

    /**
     * Servisleri kapatmak istediğimizde çağıracağımız metot.
     */
    public static void shutdown() {
        logger.info("=== AppConfig shutdown started ===");
        if (kafkaConsumerService != null) {
            kafkaConsumerService.stop();
        }
        if (consumerThread != null && consumerThread.isAlive()) {
            try {
                consumerThread.join(5000);  // max 5 sn bekliyoruz
            } catch (InterruptedException e) {
                logger.warn("Interrupted while waiting for consumerThread to finish.");
            }
        }
        if (esService != null) {
            try {
                esService.close();
            } catch (Exception e) {
                logger.error("Error closing ElasticsearchService => {}", e.getMessage(), e);
            }
        }
        logger.info("=== AppConfig shutdown completed ===");
    }

}
