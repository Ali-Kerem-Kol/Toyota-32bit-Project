package com.mydomain.consumer.rates_consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot main class => Kafka consumer microservice
 */
@SpringBootApplication
public class ConsumerPostgresqlApplication {

	public static void main(String[] args) {
		SpringApplication.run(ConsumerPostgresqlApplication.class, args);
	}

}
