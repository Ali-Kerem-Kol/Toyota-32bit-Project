package com.mydomain.main.service;

import com.mydomain.main.model.Rate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.XAddParams;
import java.util.HashMap;
import java.util.Map;

public class RedisProducerService {

    private static final Logger log = LogManager.getLogger(RedisProducerService.class);

    private final JedisPool jedisPool;
    private final String streamName;
    private final long maxStreamLength;

    public RedisProducerService(JedisPool jedisPool, String streamName, long maxStreamLength) {
        this.jedisPool = jedisPool;
        this.streamName = streamName;
        this.maxStreamLength = maxStreamLength;
    }


    public void publishRate(String rateName, Rate rate) {
        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, String> map = new HashMap<>();
            map.put("name", rateName);
            map.put("bid", String.valueOf(rate.getFields().getBid()));
            map.put("ask", String.valueOf(rate.getFields().getAsk()));
            map.put("ts", String.valueOf(rate.getFields().getTimestamp()));

            jedis.xadd(streamName, new XAddParams()
                    .approximateTrimming()
                    .maxLen(maxStreamLength), map);

            log.info("📤 Rate published to stream '{}': {}", streamName, rateName);
        } catch (Exception e) {
            log.error("❌ Failed to write to stream '{}': {}", streamName, e.getMessage(), e);
        }
    }

    public void publishRates(Map<String, Rate> rates) {
        if (rates == null || rates.isEmpty()) return;
        rates.forEach(this::publishRate);
    }
}
