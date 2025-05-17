package com.mydomain.consumer_elasticsearch.config;

import lombok.extern.log4j.Log4j2;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Tüm altyapı bean’lerini tek noktada tutar:
 *  - Elasticsearch RestHighLevelClient
 *  - Kafka ConsumerFactory  & Listener Factory
 */
@Log4j2
@Configuration
@EnableKafka
public class AppConfig {

    /* ---------- Elasticsearch ---------- */
    @Bean(destroyMethod = "close")
    public RestHighLevelClient elasticsearchClient() {
        log.info("Creating Elasticsearch client at {}://{}:{}",
                ConfigReader.getEsScheme(),
                ConfigReader.getEsHost(),
                ConfigReader.getEsPort());

        return new RestHighLevelClient(
                RestClient.builder(
                        new HttpHost(
                                ConfigReader.getEsHost(),
                                ConfigReader.getEsPort(),
                                ConfigReader.getEsScheme()
                        )
                )
        );
    }

    /* ---------- Kafka ---------- */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, ConfigReader.getKafkaBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG,          ConfigReader.getKafkaGroupId());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, ConfigReader.getAutoOffsetReset());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(1);          // istersem arttırabilirim
        factory.getContainerProperties().setIdleBetweenPolls(0L);
        return factory;
    }
}
