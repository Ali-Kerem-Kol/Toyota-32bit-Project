package com.mydomain.main.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydomain.main.exception.RedisException;
import com.mydomain.main.filter.FilterService;
import com.mydomain.main.model.Rate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import redis.clients.jedis.*;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.*;

/**
 * Redis ile iletişim kurarak hem raw hem de calculated kurların
 * saklanmasını, güncellenmesini ve durum yönetimini sağlar.
 * Performans ve tutarlılık için SCAN, LSET ve Transaction kullanır.
 */
public class RedisService {
    private static final Logger log = LogManager.getLogger(RedisService.class);

    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FilterService filterService;
    private final int REDIS_TTL;
    private final int REDIS_MAX_LIST_SIZE;

    /**
     * RedisService constructor.
     *
     * @param jedisPool         Bağlanılacak Redis havuzu
     * @param filterService     Filtre uygulama servisi
     * @param redisTtl          Her bir anahtar için TTL (saniye)
     * @param redisMaxListSize  Her bir listenin maksimum eleman sayısı
     */
    public RedisService(JedisPool jedisPool, FilterService filterService, int redisTtl, int redisMaxListSize) {
        this.jedisPool = jedisPool;
        this.filterService = filterService;
        this.REDIS_TTL = redisTtl;
        this.REDIS_MAX_LIST_SIZE = redisMaxListSize;
    }

    // =========================================================================
    // YARDIMCI: Redis SCAN ile anahtar arama (bloklamasız)
    // =========================================================================

    /**
     * Belirli bir pattern'e uyan anahtarları SCAN ile bulur.
     *
     * @param pattern Key deseni (ör: raw_rates:*)
     * @return Uyumlu anahtarlar
     */
    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        try (Jedis jedis = jedisPool.getResource()) {
            String cursor = ScanParams.SCAN_POINTER_START;
            ScanParams params = new ScanParams().match(pattern).count(100);
            do {
                ScanResult<String> result = jedis.scan(cursor, params);
                keys.addAll(result.getResult());
                cursor = result.getCursor();
            } while (!cursor.equals(ScanParams.SCAN_POINTER_START));
        }
        return keys;
    }

    // =========================================================================
    // RAW RATES: Veri ekleme, çekme ve deaktifleştirme
    // =========================================================================

    /**
     * Yeni raw rate ekler (varsa filtre uygular).
     *
     * @return 0 → ilk ekleme, 1 → güncellendi
     */
    public int putRawRate(String platform, String rateName, Rate rate) {
        String key = "raw_rates:" + platform + ":" + rateName;
        try (Jedis jedis = jedisPool.getResource()) {
            boolean isFirst = jedis.llen(key) == 0;

            if (!isFirst) {
                Rate last = deserialize(jedis.lindex(key, 0));
                if (!filterService.applyAllFilters(platform, rateName, last, rate, null)) {
                    return -1;
                }
            }

            jedis.lpush(key, serialize(rate));
            jedis.ltrim(key, 0, REDIS_MAX_LIST_SIZE - 1);
            jedis.expire(key, REDIS_TTL);
            return isFirst ? 0 : 1;

        } catch (Exception e) {
            throw new RedisException("Failed to put raw rate", e);
        }
    }

    /**
     * En güncel aktif tüm raw rate’leri (platform/rate bazında) getirir.
     */
    public Map<String, Map<String, Rate>> getMostRecentAndActiveRawRates() {
        Map<String, Map<String, Rate>> result = new HashMap<>();
        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> keys = scanKeys("raw_rates:*");
            for (String key : keys) {
                List<String> entries = jedis.lrange(key, 0, -1);
                Rate newestActive = null;
                for (String entry : entries) {
                    Rate rate = deserialize(entry);
                    if (rate.getStatus() != null && rate.getStatus().isActive()) {
                        if (newestActive == null || rate.getFields().getTimestamp() > newestActive.getFields().getTimestamp()) {
                            newestActive = rate;
                        }
                    }
                }
                if (newestActive != null) {
                    String[] parts = key.split(":");
                    if (parts.length < 3) continue;
                    String platform = parts[1];
                    String rName = parts[2];
                    result.computeIfAbsent(platform, k -> new HashMap<>()).put(rName, newestActive);
                }
            }
        } catch (Exception e) {
            throw new RedisException("Failed to retrieve most recent raw rates", e);
        }
        return result;
    }

    /**
     * Parametre olarak gelen raw rate’leri LSET ile pasifleştirir (atomic transaction).
     */
    public void deactivateRawRates(Map<String, Map<String, Rate>> rates) {
        try (Jedis jedis = jedisPool.getResource()) {
            for (String platform : rates.keySet()) {
                Map<String, Rate> platformRates = rates.get(platform);
                for (Map.Entry<String, Rate> entry : platformRates.entrySet()) {
                    String rateName = entry.getKey();
                    Rate usedRate = entry.getValue();
                    String key = "raw_rates:" + platform + ":" + rateName;
                    List<String> entries = jedis.lrange(key, 0, -1);

                    for (int i = 0; i < entries.size(); i++) {
                        Rate rate = deserialize(entries.get(i));
                        if (rate.getFields().getTimestamp() == usedRate.getFields().getTimestamp()) {
                            rate.getStatus().setActive(false);
                            String updatedJson = serialize(rate);
                            // Transaction ile güncelleme
                            Transaction t = jedis.multi();
                            t.lset(key, i, updatedJson);
                            t.expire(key, REDIS_TTL);
                            t.exec();
                            log.debug("🔻 Deactivated RAW: {}:{} @{}", platform, rateName, rate.getFields().getTimestamp());
                            //break; // sadece birini güncelle
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new RedisException("Failed to deactivate raw rates", e);
        }
    }

    // =========================================================================
    // CALCULATED RATES: Hesaplanmış veriler için SCAN ve LSET/Transaction
    // =========================================================================

    /**
     * Calculated rate ekler.
     */
    public void putCalculatedRate(String rateName, Rate rate) {
        String key = "calculated_rates:" + rateName;
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.lpush(key, serialize(rate));
            jedis.ltrim(key, 0, REDIS_MAX_LIST_SIZE - 1);
            jedis.expire(key, REDIS_TTL);
        } catch (Exception e) {
            throw new RedisException("Failed to put calculated rate", e);
        }
    }

    /**
     * En güncel aktif tüm calculated rate’leri getirir.
     */
    public List<Rate> getMostRecentAndActiveCalculatedRates() {
        List<Rate> result = new ArrayList<>();
        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> keys = scanKeys("calculated_rates:*");
            for (String key : keys) {
                List<String> entries = jedis.lrange(key, 0, -1);
                Rate newestActive = null;
                for (String entry : entries) {
                    Rate rate = deserialize(entry);
                    if (rate.getStatus() != null && rate.getStatus().isActive()) {
                        if (newestActive == null || rate.getFields().getTimestamp() > newestActive.getFields().getTimestamp()) {
                            newestActive = rate;
                        }
                    }
                }
                if (newestActive != null) {
                    result.add(newestActive);
                }
            }
        } catch (Exception e) {
            throw new RedisException("Failed to retrieve most recent calculated rates", e);
        }
        return result;
    }

    /**
     * Parametre olarak gelen rate’leri LSET ile pasifleştirir (atomic transaction).
     */
    public void deactivateCalculatedRates(List<Rate> rates) {
        try (Jedis jedis = jedisPool.getResource()) {
            for (Rate sent : rates) {
                String key = "calculated_rates:" + sent.getRateName();
                List<String> entries = jedis.lrange(key, 0, -1);

                for (int i = 0; i < entries.size(); i++) {
                    Rate rate = deserialize(entries.get(i));
                    if (rate.getFields().getTimestamp() == sent.getFields().getTimestamp()) {
                        rate.getStatus().setActive(false);
                        String updatedJson = serialize(rate);
                        // Transaction ile güncelleme
                        Transaction t = jedis.multi();
                        t.lset(key, i, updatedJson);
                        t.expire(key, REDIS_TTL);
                        t.exec();
                        log.debug("🔻 Deactivated CALCULATED: {} @{}", rate.getRateName(), rate.getFields().getTimestamp());
                        //break; // sadece birini güncelle
                    }
                }
            }
        } catch (Exception e) {
            throw new RedisException("Failed to deactivate calculated rates", e);
        }
    }

    /**
     * Tüm aktif calculated rate’leri getirir.
     */
    public List<Rate> getAllActiveCalculatedRates() {
        List<Rate> activeRates = new ArrayList<>();
        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> keys = scanKeys("calculated_rates:*");
            for (String key : keys) {
                List<String> entries = jedis.lrange(key, 0, -1);
                for (String entry : entries) {
                    Rate rate = deserialize(entry);
                    if (rate.getStatus() != null && rate.getStatus().isActive()) {
                        activeRates.add(rate);
                    }
                }
            }
        } catch (Exception e) {
            throw new RedisException("Failed to retrieve active calculated rates", e);
        }
        return activeRates;
    }

    // =========================================================================
    // JSON Serileştirme / Deserileştirme
    // =========================================================================

    private String serialize(Rate rate) throws Exception {
        return objectMapper.writeValueAsString(rate);
    }

    private Rate deserialize(String json) throws Exception {
        return objectMapper.readValue(json, Rate.class);
    }
}
