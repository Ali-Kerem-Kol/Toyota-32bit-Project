package com.mydomain.main.redis;

import com.mydomain.main.exception.RedisException;
import com.mydomain.main.model.Rate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.XAddParams;
import redis.clients.jedis.params.XTrimParams;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

public class RedisProducerService {

    private static final Logger logger = LogManager.getLogger(RedisProducerService.class);

    private final JedisPool jedisPool;
    private final String streamName;
    private final long maxStreamLength;
    private final Duration retentionWindow;
    private final ScheduledExecutorService cleaner;

    public RedisProducerService(JedisPool jedisPool,
                                String streamName,
                                long maxStreamLength,
                                Duration retentionWindow) {
        this.jedisPool = jedisPool;
        this.streamName = streamName;
        this.maxStreamLength = maxStreamLength;
        this.retentionWindow = retentionWindow;

        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "redis-stream-cleaner");
            t.setDaemon(true);
            return t;
        });

        cleaner.scheduleAtFixedRate(
                this::trimByAge,
                retentionWindow.toMillis(),
                retentionWindow.toMillis(),
                TimeUnit.MILLISECONDS
        );
    }

    public boolean publishSingleRate(Rate rate) throws RedisException {
        return publishToStream(rate);
    }

    public boolean publishMultipleRates(Collection<Rate> rates) throws RedisException {
        if (rates == null || rates.isEmpty()) return true;
        boolean allPublished = true;
        for (Rate rate : rates) {
            boolean ok = publishToStream(rate);
            if (!ok) allPublished = false;
        }
        return allPublished;
    }

    private boolean publishToStream(Rate rate) throws RedisException {
        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, String> map = new HashMap<>();
            map.put("name", rate.getRateName());
            map.put("bid", String.valueOf(rate.getFields().getBid()));
            map.put("ask", String.valueOf(rate.getFields().getAsk()));
            map.put("ts", String.valueOf(rate.getFields().getTimestamp()));

            logger.debug("Publishing rate to stream '{}': {}", streamName, map);

            jedis.xadd(
                    streamName,
                    new XAddParams()
                            .approximateTrimming()
                            .maxLen(maxStreamLength),
                    map
            );

            logger.info("📤 Rate published to stream '{}': {}", streamName, rate.getRateName());
            return true;

        } catch (Exception e) {
            logger.debug("⏱ Redis publish error (suppressed): {}", e.getMessage());
            throw new RedisException("Failed to publish to Redis stream: " + streamName, e);
        }
    }

    private void trimByAge() {
        long cutoff = Instant.now().minus(retentionWindow).toEpochMilli();
        String minId = cutoff + "-0";

        try (Jedis jedis = jedisPool.getResource()) {
            long removed = jedis.xtrim(
                    streamName,
                    new XTrimParams()
                            .minId(minId)
                            .approximateTrimming()
            );

            if (removed > 0) {
                logger.info("🧹 Stream '{}' trimmed by age. Cutoff: {} (minId={}), Removed: {} entry(ies)",
                        streamName, retentionWindow, minId, removed);
            } else {
                logger.trace("🧹 Stream '{}' already clean. No entries removed for minId={}", streamName, minId);
            }

        } catch (Exception e) {
            logger.debug("⏱ Redis trimByAge error (suppressed): {}", e.getMessage());
            // Bu işlem kritik olmadığı için exception fırlatmıyoruz
        }
    }

    public void shutdown() {
        cleaner.shutdownNow();
    }
}
