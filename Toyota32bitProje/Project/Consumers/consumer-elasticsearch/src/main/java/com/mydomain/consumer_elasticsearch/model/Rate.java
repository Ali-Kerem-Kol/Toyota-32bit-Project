package com.mydomain.consumer_elasticsearch.model;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka’dan gelen döviz kuru mesajını temsil eder
 * ve Elasticsearch’e JSON olarak indekslenir.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Rate {

    private UUID id;

    private String name;

    private double bid;

    private double ask;

    private long timestamp;

}
