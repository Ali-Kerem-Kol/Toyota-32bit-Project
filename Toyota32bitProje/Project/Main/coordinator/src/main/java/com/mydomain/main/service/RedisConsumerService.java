
package com.mydomain.main.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydomain.main.model.Rate;
import com.mydomain.main.model.RateFields;
import com.mydomain.main.model.RateStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.*;
import redis.clients.jedis.params.XReadGroupParams;
import redis.clients.jedis.resps.StreamEntry;

import java.time.OffsetDateTime;
import java.util.*;

public class RedisConsumerService {

    private static final Logger log = LogManager.getLogger(RedisConsumerService.class);

    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;
    private final String streamName;
    private final String groupName;
    private final String consumerName;
    private final int readCount;
    private final int blockMillis;

    public RedisConsumerService(JedisPool jedisPool,
                                ObjectMapper objectMapper,
                                String streamName,
                                String groupName,
                                String consumerName,
                                int readCount,
                                int blockMillis) {
        this.jedisPool = jedisPool;
        this.objectMapper = objectMapper;
        this.streamName = streamName;
        this.groupName = groupName;
        this.consumerName = consumerName;
        this.readCount = readCount;
        this.blockMillis = blockMillis;

        ensureGroupExists();
    }

    private void ensureGroupExists() {
        try (Jedis jedis = jedisPool.getResource()) {
            if (!jedis.exists(streamName)) {
                jedis.xadd(streamName, StreamEntryID.NEW_ENTRY, Map.of("init", "1"));
            }
            jedis.xgroupCreate(streamName, groupName, StreamEntryID.LAST_ENTRY, true);
            log.info("✅ Group oluşturuldu: {} (stream: {})", groupName, streamName);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                log.info("ℹ️ Group zaten var: {}", groupName);
            } else {
                log.error("❌ Group oluşturulamadı: {}", e.getMessage(), e);
            }
        }
    }

    public List<StreamEntry> readStreamEntries() {
        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, StreamEntryID> stream = Map.of(streamName, StreamEntryID.UNRECEIVED_ENTRY);
            List<Map.Entry<String, List<StreamEntry>>> entries = jedis.xreadGroup(
                    groupName,
                    consumerName,
                    XReadGroupParams.xReadGroupParams()
                            .block(blockMillis)
                            .count(readCount),
                    stream
            );
            if (entries == null || entries.isEmpty()) return List.of();
            return entries.get(0).getValue();
        } catch (Exception e) {
            log.error("❌ Stream okuma hatası: {}", e.getMessage(), e);
            return List.of();
        }
    }

    public void acknowledge(StreamEntry entry) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.xack(streamName, groupName, entry.getID());
            log.info("✅ ACK: {}", entry.getID());
        } catch (Exception e) {
            log.error("❌ ACK hatası: {}", e.getMessage(), e);
        }
    }

    public Rate parseStreamEntry(StreamEntry entry) {
        try {
            Map<String, String> map = entry.getFields();
            String name = map.get("name");
            double bid = Double.parseDouble(map.get("bid"));
            double ask = Double.parseDouble(map.get("ask"));
            long ts;
            String tsValue = map.get("ts");

            try {
                ts = Long.parseLong(tsValue);
            } catch (NumberFormatException e) {
                // ISO formatı geldiyse UTC'ye göre epoch millis'e çevir
                ts = OffsetDateTime.parse(tsValue).toInstant().toEpochMilli();
            }


            return new Rate(name, new RateFields(bid, ask, ts), new RateStatus(true, true));
        } catch (Exception e) {
            log.error("parseStreamEntry error: {}", e.getMessage());
            return null;
        }
    }

    public Map<String, List<Rate>> readAndGroupRawRates() {
        Map<String, List<Rate>> groupedRates = new HashMap<>();
        List<StreamEntry> entries = readStreamEntries();
        for (StreamEntry entry : entries) {
            Rate rate = parseStreamEntry(entry);
            if (rate == null) continue;
            String fullName = rate.getRateName();
            String shortName = fullName.substring(fullName.indexOf('_') + 1);
            groupedRates.computeIfAbsent(shortName, k -> new ArrayList<>()).add(rate);
            acknowledge(entry);
        }
        return groupedRates;
    }

    public Map<String, Rate> readAndGroupCalculatedRates() {
        Map<String, Rate> calculatedRates = new HashMap<>();
        List<StreamEntry> entries = readStreamEntries();
        for (StreamEntry entry : entries) {
            Rate rate = parseStreamEntry(entry);
            if (rate == null) continue;
            calculatedRates.put(rate.getRateName(), rate);
            acknowledge(entry);
        }
        return calculatedRates;
    }
}
