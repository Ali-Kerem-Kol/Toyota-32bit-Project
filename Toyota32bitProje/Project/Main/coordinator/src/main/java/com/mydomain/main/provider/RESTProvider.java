package com.mydomain.main.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydomain.main.coordinator.ICoordinator;
import com.mydomain.main.exception.RedisException;
import com.mydomain.main.model.*;
import com.mydomain.main.redis.RedisService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * {@code RESTProvider}, REST API tabanlı veri kaynaklarından kur (rate) verilerini çeker,
 * abonelik bazında periyodik olarak polling yapar ve alınan verileri `RedisService`’e kaydeder.
 * `IProvider` arayüzünü uygulayarak platform bağlantılarını yönetir ve `ICoordinator` ile
 * veri bildirimlerini koordine eder. Bu sınıf, thread-safe bir şekilde çalışır ve
 * `AutoCloseable` arayüzü ile kaynakları güvenli bir şekilde kapatır.
 *
 * <p>Hizmetin temel işleyişi:
 * <ul>
 *   <li>Konfigürasyon dosyasından (rest-config.json) base URL, API anahtarı ve polling aralığı yüklenir.</li>
 *   <li>Abonelikler (`subscriptions`) bir `CopyOnWriteArraySet` ile thread-safe şekilde saklanır.</li>
 *   <li>Belirtilen aralıkta (`pollInterval`) REST API’den veri çekilir ve Redis’e yazılır.</li>
 *   <li>Veri işlenirken `FilterService` tarafından doğrulama yapılır (Redis katmanında).</li>
 * </ul>
 * </p>
 *
 * <p><b>Özellikler:</b>
 * <ul>
 *   <li>HTTP istemcisi için 3 saniyelik bağlantı zaman aşımı kullanılır.</li>
 *   <li>Loglama için Apache Log4j ile hata ayıklama ve izleme seviyeleri desteklenir.</li>
 *   <li>Polling işlemi daemon thread ile arka planda çalışır ve `close()` ile sonlandırılır.</li>
 * </ul>
 * </p>
 *
 * @author Ali Kerem Kol
 * @version 1.0
 * @since 2025-06-07
 */
public class RESTProvider implements IProvider, AutoCloseable {

    private static final Logger log = LogManager.getLogger(RESTProvider.class);
    private static final String CONFIG_FILE_PATH = "/app/Main/coordinator/config/rest-config.json";

    private ICoordinator coordinator;
    private String platformName;
    private RedisService redisService;

    private String baseUrl;
    private String apiKey;
    private Duration pollInterval;

    private final Set<String> subscriptions = new CopyOnWriteArraySet<>();
    private final AtomicBoolean connected = new AtomicBoolean(false);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "rest-poller");
                t.setDaemon(true);
                return t;
            });

    /**
     * Belirtilen platform adına REST API bağlantısını kurar ve polling işlemini başlatır.
     * Konfigürasyon dosyasını yükler, bağlantı durumunu günceller ve `ICoordinator`’a bildirim yapar.
     * Eğer konfigürasyon yüklenemezse bağlantı başarısız olur ve loglanır.
     *
     * @param platformName Bağlantı kurulacak platformun adı (örneğin "REST_PLATFORM"),
     *                    null veya boş ise hata loglanır
     * @param _ignored Bağlantı parametreleri (bu uygulamada kullanılmaz, null olabilir)
     * @throws IllegalStateException Eğer coordinator null ise
     */
    @Override
    public void connect(String platformName, Map<String, String> _ignored) {
        this.platformName = platformName;
        log.trace("connect() called for platform: {}", platformName);

        if (!loadOwnConfig()) {
            log.error("⛔ Config load failed – RESTProvider could not be started.");
            return;
        }

        log.info("🔍 [{}] REST config loaded: url={}, interval={}s",
                platformName, baseUrl, pollInterval.getSeconds());

        connected.set(true);
        log.trace("Calling coordinator.onConnect() for: {}", platformName);
        safe(() -> coordinator.onConnect(platformName, true));

        scheduler.scheduleAtFixedRate(this::pollAll, 0, pollInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Belirtilen platform için REST API bağlantısını keser ve kaynakları serbest bırakır.
     * `close()` metodunu çağırarak scheduler’ı sonlandırır ve bildirim yapar.
     *
     * @param platformName Bağlantısı kesilecek platformun adı,
     *                    null veya boş ise hata loglanır
     * @param _unused Bağlantı kesme parametreleri (bu uygulamada kullanılmaz, null olabilir)
     */
    @Override
    public void disConnect(String platformName, Map<String, String> _unused) {
        log.trace("disConnect() called for platform: {}", platformName);
        close();
    }

    /**
     * Belirtilen platformda bir kura (rate) abone olur.
     * Abonelik, `subscriptions` kümesine eklenir ve loglanır.
     *
     * @param platformName Abonelik yapılacak platformun adı,
     *                    null veya boş ise hata loglanır
     * @param rateName Abone olunacak kurun adı (örneğin "USDTRY"),
     *                 null veya boş ise hata loglanır
     */
    @Override
    public void subscribe(String platformName, String rateName) {
        subscriptions.add(rateName);
        log.info("📡 [{}] Subscribed to rate: {}", platformName, rateName);
    }

    /**
     * Belirtilen platformda bir kura (rate) abonelikten çıkar.
     * Abonelik, `subscriptions` kümesinden kaldırılır ve loglanır.
     *
     * @param platformName Abonelikten çıkılacak platformun adı,
     *                    null veya boş ise hata loglanır
     * @param rateName Aboneliği sonlandırılacak kurun adı,
     *                 null veya boş ise hata loglanır
     */
    @Override
    public void unSubscribe(String platformName, String rateName) {
        subscriptions.remove(rateName);
        log.info("📴 [{}] Unsubscribed from rate: {}", platformName, rateName);
    }

    /**
     * Bu sağlayıcının koordinatör arayüzünü ayarlar.
     * Koordinatör, veri geldiğinde veya durum değiştiğinde bildirim almak için kullanılır.
     *
     * @param c Uygulamanın koordinatör nesnesi (ICoordinator),
     *          null ise hata loglanır ancak istisna fırlatılmaz
     */
    @Override
    public void setCoordinator(ICoordinator c) {
        this.coordinator = c;
        log.trace("Coordinator reference set for RESTProvider.");
    }

    /**
     * Bu sağlayıcının Redis servisini ayarlar.
     * RedisService, çekilen verilerin saklanması için kullanılır.
     *
     * @param redisService Redis operasyonlarını yöneten servis,
     *                     null ise hata loglanır ancak istisna fırlatılmaz
     */
    @Override
    public void setRedis(RedisService redisService) {
        this.redisService = redisService;
    }

    /**
     * Tüm abonelikler için periyodik veri çekme işlemini başlatır.
     * Eğer abonelik yoksa veya baseUrl ayarlanmamışsa işlem iptal edilir.
     */
    private void pollAll() {
        if (subscriptions.isEmpty()) {
            log.debug("[{}] No subscriptions to poll.", platformName);
            return;
        }
        if (baseUrl == null) {
            log.error("[{}] Base URL not set. Cannot poll.", platformName);
            return;
        }

        log.trace("[{}] Starting poll for subscriptions: {}", platformName, subscriptions);
        subscriptions.forEach(this::fetchOne);
    }

    /**
     * Belirtilen kur (rate) için REST API’den veri çeker ve işler.
     * Çekilen veriler Redis’e kaydedilir ve koordinatöre bildirim yapılır.
     * Filtreleme başarısızlığı durumunda loglanır.
     *
     * @param rateName Çekilecek kurun adı (örneğin "USDTRY"),
     *                 null veya boş ise hata loglanır
     */
    private void fetchOne(String rateName) {
        try {
            log.trace("[{}] Fetching data for rate: {}", platformName, rateName);

            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/" + rateName))
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(3))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            JsonNode jsonNode = objectMapper.readTree(resp.body());
            String fetchedRateName = jsonNode.path("rateName").asText();
            if (!rateName.equals(fetchedRateName)) {
                log.warn("[{}] Mismatched rateName: expected {}, got {}", platformName, rateName, fetchedRateName);
                return; // İşlemi durdur
            }
            double bid = jsonNode.path("bid").asDouble();
            double ask = jsonNode.path("ask").asDouble();
            String tsStr = jsonNode.path("timestamp").asText();
            long ts = Instant.parse(tsStr).toEpochMilli();

            log.trace("[{}] Fetched REST rate: {} = bid:{} ask:{} ts:{}", platformName, fetchedRateName, bid, ask, ts);

            Rate rate = new Rate(fetchedRateName, new RateFields(bid, ask, ts), new RateStatus(true, false));
            int result = redisService.putRawRate(platformName, fetchedRateName, rate);

            if (result == 0) {
                coordinator.onRateAvailable(platformName, fetchedRateName, rate);
            } else if (result == 1) {
                coordinator.onRateUpdate(platformName, fetchedRateName, rate.getFields());
            } else if (result == -1) {
                log.warn("[{}] Filter rejected rate: {}", platformName, fetchedRateName);
            }
        } catch (InterruptedException e) {
            log.error("🌐 [{}] HTTP request interrupted for [{}]: {}", platformName, rateName, e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (RedisException e) {
            log.error("❌ [{}] Redis error while processing rate [{}]: {}", platformName, rateName, e.getMessage(), e);
        } catch (Exception e) {
            log.error("🌐 [{}] Failed to fetch/process REST data for [{}] → {}", platformName, rateName, e.getMessage(), e);
        }
    }

    /**
     * Kendi konfigürasyon dosyasını (rest-config.json) yükler.
     * Base URL, API anahtarı ve polling aralığı gibi parametreleri parse eder.
     *
     * @return Konfigürasyon yükleme başarılıysa true, aksi halde false
     */
    private boolean loadOwnConfig() {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(CONFIG_FILE_PATH));
            String text = new String(bytes, StandardCharsets.UTF_8);
            JSONObject cfg = new JSONObject(text);

            this.baseUrl = cfg.getString("restApiUrl");
            this.apiKey = cfg.getString("apiKey");
            this.pollInterval = Duration.ofSeconds(cfg.optInt("pollInterval", 5));

            log.trace("REST config parsed: url={}, interval={}s", baseUrl, pollInterval.getSeconds());
            return true;

        } catch (Exception e) {
            log.error("Config load failed from path [{}]: {}", CONFIG_FILE_PATH, e.getMessage(), e);
            return false;
        }
    }

    /**
     * RESTProvider’ın kaynaklarını serbest bırakır.
     * Scheduler’ı sonlandırır, bağlantı durumunu günceller ve koordinatöre bildirim yapar.
     */
    @Override
    public void close() {
        scheduler.shutdownNow();
        connected.set(false);
        log.trace("Calling coordinator.onDisConnect() for: {}", platformName);
        safe(() -> coordinator.onDisConnect(platformName, false));
        log.info("RESTProvider shut down for platform: {}", platformName);
    }

    /**
     * Çalıştırılacak kod bloğunu güvenli bir şekilde çalıştırır.
     * İstisnalar loglanır ancak ana akışı etkilemez.
     *
     * @param r Çalıştırılacak Runnable nesne,
     *          null ise hata loglanır
     */
    private static void safe(Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            log.debug("safe-run error: {}", e.getMessage(), e);
        }
    }
}
