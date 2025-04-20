package com.mydomain.main.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydomain.main.model.Rate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;

public class RedisService {

    private static final Logger logger = LogManager.getLogger(RedisService.class);
    private Jedis jedis;
    private final String host;
    private final int port;
    private final ObjectMapper objectMapper;
    private final long RECONNECT_INTERVAL_MS = 5000; // 5 saniye

    public RedisService(String host, int port) {
        this.host = host;
        this.port = port;
        this.objectMapper = new ObjectMapper();
        connect();
        startConnectionMonitor();
    }

    /**
     * Redis sunucusuna bağlantıyı kurar.
     */
    private synchronized void connect() {
        try {
            if (jedis != null) {
                try {
                    jedis.close();
                } catch (Exception e) {
                    logger.error("Error closing old Redis connection: {}", e.getMessage());
                }
            }
            jedis = new Jedis(host, port);
            String pingResponse = jedis.ping();
            if ("PONG".equals(pingResponse)) {
                logger.info("Connected to Redis at {}:{}", host, port);
            }
        } catch (Exception e) {
            logger.error("Failed to connect to Redis at {}:{}: {}", host, port, e.getMessage());
            jedis = null;
        }
    }

    /**
     * Bağlantı durumunu periyodik olarak kontrol eden ve gerektiğinde yeniden bağlanmayı deneyen daemon thread.
     */
    private void startConnectionMonitor() {
        Thread monitor = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(RECONNECT_INTERVAL_MS);
                    if (jedis == null || !isConnected()) {
                        logger.warn("Redis connection lost. Attempting to reconnect...");
                        connect();
                    }
                } catch (InterruptedException ie) {
                    logger.error("Redis connection monitor thread interrupted: {}", ie.getMessage());
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in Redis connection monitor: {}", e.getMessage());
                }
            }
        });
        monitor.setDaemon(true);
        monitor.start();
    }

    /**
     * Redis bağlantısının geçerli olup olmadığını test eder.
     */
    private synchronized boolean isConnected() {
        try {
            if (jedis != null) {
                return "PONG".equals(jedis.ping());
            }
        } catch (JedisConnectionException e) {
            return false;
        }
        return false;
    }

    /**
     * Belirli bir önek (prefix) ile rate kaydeder.
     */
    private synchronized void putRateWithPrefix(String prefix, String rateName, Rate rate) {
        try {
            String rateJson = objectMapper.writeValueAsString(rate);
            jedis.set(prefix + rateName, rateJson);
        } catch (JedisConnectionException jce) {
            logger.error("Error saving rate {} to Redis with prefix {}: {}", rateName, prefix, jce.getMessage(), jce);
            connect(); // Hata durumunda yeniden bağlanmayı dene.
        } catch (Exception e) {
            logger.error("Error saving rate {} to Redis with prefix {}: {}", rateName, prefix, e.getMessage(), e);
        }
    }

    /**
     * Belirli bir önek (prefix) ile rate bilgisini getirir.
     */
    private synchronized Rate getRateWithPrefix(String prefix, String rateName) {
        try {
            String rateJson = jedis.get(prefix + rateName);
            if (rateJson == null) {
                return null;
            }
            return objectMapper.readValue(rateJson, Rate.class);
        } catch (JedisConnectionException jce) {
            logger.error("Error retrieving rate {} from Redis with prefix {}: {}", rateName, prefix, jce.getMessage());
            connect(); // Hata durumunda yeniden bağlanmayı dene.
            return null;
        } catch (Exception e) {
            logger.error("Error retrieving rate {} from Redis with prefix {}: {}", rateName, prefix, e.getMessage());
            return null;
        }
    }

    // "Raw Rates" için metodlar
    public void putRawRate(String rateName, Rate rate) {
        putRateWithPrefix("raw:", rateName, rate);
    }

    public Rate getRawRate(String rateName) {
        return getRateWithPrefix("raw:", rateName);
    }

    // "Calculated Rates" için metodlar
    public void putCalculatedRate(String rateName, Rate rate) {
        putRateWithPrefix("calculated:", rateName, rate);
    }

    public Rate getCalculatedRate(String rateName) {
        return getRateWithPrefix("calculated:", rateName);
    }


    // İsteğe bağlı: Varsayılan (prefix olmadan) metodlar
    public void putRate(String rateName, Rate rate) {
        putRateWithPrefix("", rateName, rate);
    }

    public Rate getRate(String rateName) {
        return getRateWithPrefix("", rateName);
    }

}
