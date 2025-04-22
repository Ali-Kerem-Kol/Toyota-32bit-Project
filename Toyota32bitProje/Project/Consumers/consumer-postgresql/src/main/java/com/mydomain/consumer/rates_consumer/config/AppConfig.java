package com.mydomain.consumer.rates_consumer.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka tüketici (consumer) yapılandırmasını sağlar.
 * Bu sınıf, Spring Kafka ile mesajları dinlemek için gereken
 * ConsumerFactory ve ConcurrentKafkaListenerContainerFactory
 * bean'lerini tanımlar.
 */
@Configuration
@EnableKafka
public class AppConfig {

    private static final Logger logger = LogManager.getLogger(AppConfig.class);

    /**
     * Kafka ConsumerFactory bean'i oluşturur.
     * Bootstrap sunucuları, grup kimliği ve offset reset stratejisi
     * config.json'dan okunarak ayarlanır.
     *
     * @return ConsumerFactory<String, String> Kafka tüketici fabrikası
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        String bootstrapServers = ConfigReader.getKafkaBootstrapServers();
        String offsetReset = ConfigReader.getAutoOffsetReset();  // "latest" veya "earliest"
        String groupId = ConfigReader.getGroupId();              // "ratesConsumerGroup"

        logger.info("Creating ConsumerFactory with bootstrap={}, offsetReset={}, groupId={}",
                bootstrapServers, offsetReset, groupId);

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, offsetReset);

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * KafkaListenerContainerFactory bean'i oluşturur.
     * Bu factory, @KafkaListener ile işaretlenmiş yöntemlerin
     * arka planda çoklu iş parçacıklı dinleme yapmasını sağlar.
     *
     * @return ConcurrentKafkaListenerContainerFactory<String, String> Kafka dinleme konteyner fabrikası
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}
