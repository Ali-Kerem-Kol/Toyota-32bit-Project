package com.mydomain.main.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class RateProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topicName = "rates-topic"; // Dokümanda bu formatta bir konu olduğundan emin ol

    @Autowired
    public RateProducerService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendRate(String rateMessage) {
        kafkaTemplate.send(topicName, rateMessage);
        System.out.println("✅ Sent to Kafka -> " + topicName + ": " + rateMessage);
    }
}
