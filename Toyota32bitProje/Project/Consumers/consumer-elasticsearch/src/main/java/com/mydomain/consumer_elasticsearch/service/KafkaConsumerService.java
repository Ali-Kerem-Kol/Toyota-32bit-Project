package com.mydomain.consumer_elasticsearch.service;

import com.mydomain.consumer_elasticsearch.model.Rate;
import com.mydomain.consumer_elasticsearch.model.RateFields;
import com.mydomain.consumer_elasticsearch.model.RateStatus;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

public class KafkaConsumerService {

    private static final Logger logger = LogManager.getLogger(KafkaConsumerService.class);

    private final KafkaConsumer<String, String> consumer;
    private final ElasticsearchService esService;
    private volatile boolean running = true;

    public KafkaConsumerService(String bootstrapServers, String groupId, String topic, ElasticsearchService esService) {
        this.esService = esService;

        // Kafka ayarları
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        this.consumer = new KafkaConsumer<>(props);
        this.consumer.subscribe(Collections.singletonList(topic));
    }

    // Sonsuz döngü ile poll yapacağız,
    // "running" false olduğunda çıkacağız.
    public void start() {
        logger.info("Kafka consumer started... (polling messages)");

        try {
            while (running) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                for (ConsumerRecord<String, String> record : records) {
                    logger.debug("Received message => partition={}, offset={}, value={}",
                            record.partition(), record.offset(), record.value());

                    Rate rate = parseRate(record.value());
                    if (rate != null) {
                        esService.indexRate(rate);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Kafka consumer error => {}", e.getMessage(), e);
        } finally {
            consumer.close();
            logger.info("Kafka consumer closed.");
        }
    }

    // Uygulamayı kapatırken "running" false olacak
    public void stop() {
        logger.info("Stopping KafkaConsumerService...");
        running = false;
    }

    private Rate parseRate(String message) {
        // Örnek format: "USDTRY|19.20|19.22|2025-04-05T12:00:00Z"
        try {
            String[] parts = message.split("\\|");
            String rateName = parts[0];
            double bid = Double.parseDouble(parts[1]);
            double ask = Double.parseDouble(parts[2]);
            String timestamp = parts[3];

            RateFields fields = new RateFields(bid, ask, timestamp);
            RateStatus status = new RateStatus(true, true);
            return new Rate(rateName, fields, status);
        } catch (Exception e) {
            logger.warn("Failed to parse rate => raw={}", message);
            return null;
        }
    }
}
