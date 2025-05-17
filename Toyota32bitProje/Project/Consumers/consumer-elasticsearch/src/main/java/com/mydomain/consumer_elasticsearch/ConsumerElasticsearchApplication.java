package com.mydomain.consumer_elasticsearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * consumer-elasticsearch mikroservisinin giriş noktası.
 */
@SpringBootApplication
public class ConsumerElasticsearchApplication {

	public static void main(String[] args) {
		SpringApplication.run(ConsumerElasticsearchApplication.class, args);
	}
}
