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
 * {@code RedisService}, sistemde ham (raw) ve hesaplanmış (calculated) kurların saklanmasını,
 * güncellenmesini, pasifleştirilmesini ve durum yönetimini sağlayan bir Redis istemci servisidir.
 * Performans ve tutarlılık için bloklamasız `SCAN` komutu, liste operasyonları (`LPUSH`, `LSET`),
 * atomik transaction’lar ve TTL (Time-To-Live) mekanizması kullanır. Bu sınıf, platformlardan
 * gelen verileri önbelleğe alır, filtreleme ile doğrulama yapar ve Coordinator tarafından
 * hesaplama süreçlerine destek verir.
 *
 * <p>Hizmetin temel işlevleri:
 * <ul>
 *   <li>Ham kurları (`raw_rates`) platform ve rateName bazında saklar, en güncel aktif veriyi döndürür.</li>
 *   <li>Hesaplanmış kurları (`calculated_rates`) rateName bazında saklar ve aktif/passif durumlarını yönetir.</li>
 *   <li>Filtreleme servisi ile iş birliği yaparak veri tutarlılığını sağlar.</li>
 *   <li>Redis havuzunu (JedisPool) kullanarak bağlantı yönetimini optimize eder.</li>
 * </ul>
 * </p>
 *
 * <p><b>Özellikler:</b>
 * <ul>
 *   <li>Her anahtar için özelleştirilebilir TTL (saniye) ve maksimum liste boyutu (REDIS_MAX_LIST_SIZE).</li>
 *   <li>Hata yönetimi için {@link RedisException} fırlatılır ve Apache Log4j ile detaylı loglama yapılır.</li>
 *   <li>Thread-safe operasyonlar için JedisPool ve transaction mekanizması kullanılır.</li>
 * </ul>
 * </p>
 *
 * @author Ali Kerem Kol
 * @version 1.0
 * @since 2025-06-07
 */
public class RedisService {
    private static final Logger log = LogManager.getLogger(RedisService.class);

    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FilterService filterService;
    private final int REDIS_TTL;
    private final int REDIS_MAX_LIST_SIZE;

    /**
     * {@code RedisService} nesnesini oluşturur ve gerekli bağımlılıkları initialize eder.
     * Bu constructor, Redis bağlantı havuzunu, filtreleme servisini ve yapılandırma parametrelerini
     * (TTL ve maksimum liste boyutu) alır. Null veya geçersiz parametreler hata fırlatmaz,
     * ancak loglanır.
     *
     * @param jedisPool Redis bağlantıları için kullanılacak JedisPool nesnesi,
     *                  null ise hata loglanır ve işlemler başarısız olur
     * @param filterService Filtreleme işlemlerini gerçekleştiren servis,
     *                      null ise filtreleme atlanır
     * @param redisTtl Her anahtar için geçerli olacak TTL süresi (saniye),
     *                 0 veya negatifse varsayılan olarak 0 (sonsuz) kabul edilir
     * @param redisMaxListSize Her listenin maksimum eleman sayısı,
     *                         0 veya negatifse varsayılan olarak 100 kabul edilir
     * @throws IllegalArgumentException Eğer jedisPool null ise
     */
    public RedisService(JedisPool jedisPool, FilterService filterService, int redisTtl, int redisMaxListSize) {
        if (jedisPool == null) throw new IllegalArgumentException("JedisPool cannot be null");

        this.jedisPool = jedisPool;
        this.filterService = filterService;
        this.REDIS_TTL = redisTtl;
        this.REDIS_MAX_LIST_SIZE = redisMaxListSize;
    }

    // =========================================================================
    // YARDIMCI: Redis SCAN ile anahtar arama (bloklamasız)
    // =========================================================================

    /**
     * Belirli bir pattern'e (desen) uyan anahtarları Redis'in `SCAN` komutu ile
     * bloklamasız bir şekilde tarar ve döndürür. Bu metod, büyük veri setlerinde
     * performansı artırmak için cursor tabanlı tarama kullanır.
     *
     * @param pattern Aranacak anahtar deseni (örneğin "raw_rates:*" veya "calculated_rates:*"),
     *                null veya boş ise boş bir küme döndürülür
     * @return Pattern'e uyan anahtarların kümesi (Set<String>),
     *         her zaman dolu bir küme veya boş küme döner
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
     * Yeni bir ham kur (raw rate) ekler ve gerekiyorsa filtre uygular.
     * Eğer veri ilk ekleniyorsa 0, güncelleniyorsa 1, filtre başarısızsa -1 döndürür.
     * TTL ve maksimum liste boyutu sınırları içinde saklar.
     *
     * @param platform Verinin geldiği platform adı (örneğin "REST_PLATFORM"),
     *                 null veya boş ise hata loglanır
     * @param rateName Eklenen kurun adı (örneğin "USDTRY"),
     *                 null veya boş ise hata loglanır
     * @param rate Eklenen ham kur nesnesi (Rate),
     *             null ise hata loglanır ve istisna fırlatılır
     * @return 0 (ilk ekleme), 1 (güncelleme), -1 (filtre başarısız)
     * @throws RedisException Redis operasyonunda hata oluşursa
     * @throws IllegalArgumentException Eğer rate null ise
     */
    public int putRawRate(String platform, String rateName, Rate rate) {
        if (rate == null) {
            throw new IllegalArgumentException("Rate cannot be null");
        }
        if (platform == null || platform.isEmpty() || rateName == null || rateName.isEmpty()) {
            throw new IllegalArgumentException("Platform or rateName cannot be null or empty");
        }

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
     * En güncel ve aktif ham kurları (platform/rate bazında) döndürür.
     * Her platform için yalnızca en yeni zaman damgalı aktif veriyi seçer.
     *
     * @return Platform ve rateName bazında gruplanmış aktif ham kurların haritası
     *         (Map<Platform, Map<RateName, Rate>>), boş olabilir
     * @throws RedisException Redis tarama veya deserializasyon hatası oluşursa
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
     * Belirtilen ham kurları atomik transaction ile pasifleştirir.
     * Her platform/rate için zaman damgasına göre eşleşen veriyi günceller.
     *
     * @param rates Pasifleştirilecek ham kurların haritası
     *              (Map<Platform, Map<RateName, Rate>>), null veya boş olabilir
     * @throws RedisException Redis güncelleme veya transaction hatası oluşursa
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
     * Hesaplanmış bir kuru Redis'e ekler.
     * TTL ve maksimum liste boyutu sınırları içinde saklar.
     *
     * @param rateName Eklenen hesaplanmış kurun adı (örneğin "USDTRY"),
     *                 null veya boş ise hata loglanır
     * @param rate Eklenen hesaplanmış kur nesnesi (Rate),
     *             null ise hata loglanır ve istisna fırlatılır
     * @throws RedisException Redis ekleme hatası oluşursa
     * @throws IllegalArgumentException Eğer rate veya rateName null ise
     */
    public void putCalculatedRate(String rateName, Rate rate) {
        if (rate == null || rateName == null || rateName.isEmpty()) {
            throw new IllegalArgumentException("Rate and rateName cannot be null or empty");
        }

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
     * En güncel ve aktif hesaplanmış kurları döndürür.
     * Her rateName için yalnızca en yeni zaman damgalı aktif veriyi seçer.
     *
     * @return Aktif hesaplanmış kurların listesi (List<Rate>),
     *         boş olabilir
     * @throws RedisException Redis tarama veya deserializasyon hatası oluşursa
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
     * Belirtilen hesaplanmış kurları atomik transaction ile pasifleştirir.
     * Her rate için zaman damgasına göre eşleşen veriyi günceller.
     *
     * @param rates Pasifleştirilecek hesaplanmış kurların listesi (List<Rate>),
     *              null veya boş olabilir
     * @throws RedisException Redis güncelleme veya transaction hatası oluşursa
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
     * Tüm aktif hesaplanmış kurları döndürür.
     * Her rateName için aktif durumdaki tüm verileri listeler.
     *
     * @return Aktif hesaplanmış kurların listesi (List<Rate>),
     *         boş olabilir
     * @throws RedisException Redis tarama veya deserializasyon hatası oluşursa
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

    /**
     * Bir Rate nesnesini JSON string'ine serileştirir.
     * Jackson ObjectMapper ile gerçekleştirilir.
     *
     * @param rate Serileştirilecek Rate nesnesi,
     *             null ise hata loglanır ve istisna fırlatılır
     * @return Serileştirilmiş JSON string
     * @throws Exception Serileştirme hatası oluşursa
     * @throws IllegalArgumentException Eğer rate null ise
     */
    private String serialize(Rate rate) throws Exception {
        return objectMapper.writeValueAsString(rate);
    }

    /**
     * Bir JSON string'ini Rate nesnesine deserileştirir.
     * Jackson ObjectMapper ile gerçekleştirilir.
     *
     * @param json Deserileştirilecek JSON string,
     *             null veya boş ise hata loglanır ve istisna fırlatılır
     * @return Deserileştirilmiş Rate nesnesi
     * @throws Exception Deserileştirme hatası oluşursa
     * @throws IllegalArgumentException Eğer json null veya boş ise
     */
    private Rate deserialize(String json) throws Exception {
        return objectMapper.readValue(json, Rate.class);
    }
}
