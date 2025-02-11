package com.mydomain.main.config;

import com.mydomain.main.coordinator.Coordinator;
import com.mydomain.main.coordinator.CoordinatorInterface;
import com.mydomain.main.http.HttpService;
import com.mydomain.main.provider.RESTProvider;
import com.mydomain.main.provider.TCPProvider;
import com.mydomain.main.service.RateProducerService;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
public class AppConfig {

    @Bean
    public HttpService httpService() {
        return new HttpService(ConfigReader.getRestApiKey());
    }

    @Bean
    public TCPProvider tcpProvider() {
        // Sadece host ve port parametrelerinden TCPProvider oluşturuyoruz
        return new TCPProvider(ConfigReader.getTcpHost(), ConfigReader.getTcpPort());
    }

    @Bean
    public RESTProvider restProvider(HttpService httpService) {
        // RESTProvider'ın constructor'ında Coordinator yok
        // Daha sonra "setCoordinator(...)" ile bağlayacağız
        return new RESTProvider(httpService, ConfigReader.getRestApiUrl());
    }

    /**
     * RateProducerService bean’ini, otomatik üretilen KafkaTemplate’i alarak oluşturuyoruz.
     * Spring Boot Starter Kafka -> KafkaTemplate<String, String> otomatik bean olarak bulunur.
     */
    @Bean
    public RateProducerService rateProducerService(KafkaTemplate<String, String> kafkaTemplate) {
        return new RateProducerService(kafkaTemplate);
    }

    /**
     * Koordinatör bean’i oluşturuyor; REST & TCP providerlara “setCoordinator” diyerek
     * circular dependency sorununu çözüyor.
     */
    @Bean
    public CoordinatorInterface coordinator(RESTProvider restProvider,
                                            TCPProvider tcpProvider,
                                            RateProducerService producerService) {
        // 1) Koordinatörü oluştur
        Coordinator coordinator = new Coordinator(restProvider, tcpProvider, producerService);

        // 2) Providerlara koordinatör referansını ver
        restProvider.setCoordinator(coordinator);
        tcpProvider.setCoordinator(coordinator);

        // 3) Artık Koordinatör, REST & TCP arasında null problemi olmadan haberleşebilir
        return coordinator;
    }

    /**
     * Uygulama ayağa kalkınca otomatik çalışacak.
     * REST & TCP connection metotlarını tetikleyebilirsiniz.
     */
    @Bean
    public ApplicationRunner runner(CoordinatorInterface coordinator) {
        return args -> {
            // TCP connect
            coordinator.getTcpProvider().connect("TCP_PROVIDER", null, null);

            // REST connect
            coordinator.getRestProvider().connect("REST_PROVIDER", null, null);
        };
    }
}
