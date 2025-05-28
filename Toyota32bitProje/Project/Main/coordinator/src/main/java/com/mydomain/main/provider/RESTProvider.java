package com.mydomain.main.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydomain.main.coordinator.ICoordinator;
import com.mydomain.main.model.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class RESTProvider implements IProvider, AutoCloseable {

    /* ───────── static ───────── */
    private static final Logger LOG = LogManager.getLogger(RESTProvider.class);
    private static final String CONFIG_FILE = "rest-config.json";

    /* ───────── wiring ───────── */
    private ICoordinator coordinator;
    private String platformName;

    /* bağlantı parametreleri */
    private String baseUrl;
    private String apiKey;
    private Duration pollInterval;

    /* ───────── state ───────── */
    private final Set<String> subscriptions = new CopyOnWriteArraySet<>();
    private final Set<String> notifiedRates = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean connected   = new AtomicBoolean(false);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "rest-poller");
                t.setDaemon(true);
                return t;
            });

    /* ═════ IProvider ═════ */

    @Override
    public void connect(String platformName, Map<String, String> _ignored) {
        this.platformName = platformName;

        if (!loadOwnConfig()) {
            LOG.error("⛔ Config load failed – RESTProvider could not be started.");
            return;
        }
        LOG.info("🔍 REST config  url={} interval={}s", baseUrl, pollInterval.getSeconds());

        connected.set(true);
        safe(() -> coordinator.onConnect(platformName, true));

        scheduler.scheduleAtFixedRate(this::pollAll, 0, pollInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void disConnect(String _plat, Map<String, String> _unused) {
        close();
    }

    @Override
    public void subscribe(String _plat, String rateName) {
        subscriptions.add(rateName);                       // set'e ekle
    }

    @Override
    public void unSubscribe(String _plat, String rateName) {
        subscriptions.remove(rateName);
        //notifiedRates.remove(rateName); // Hmm subscribe metodunda bu rate e bir şey eklemiyoruz ama şimdi de silmeye çalışıyoruz burada hata fırlatılabilir
                                        // Aslında hatta buna gerek yok ki ?? Çünkü bu zaten güncel abonelikleri tutmuyor, yani bir şeyden abonelikten çıkmış
                                        // isek notifiedRates den silmemiz saçma zaten abone olmuşsak eğer o rate önceden gelmiştir ki ??
                                        // Yani tekrar abone olursak zaten notifiedRates in bunu ilk kez geliyormuş gibi davranmaması gerekiyor.
                                        // O yüzden bu satırı yorum satırına aldım sonra düşünürüm ne yapacağımı
    }

    @Override
    public void setCoordinator(ICoordinator c) {
        this.coordinator = c;
    }

    /* ═════ polling ═════ */

    private void pollAll() {
        if (subscriptions.isEmpty() || baseUrl == null) return;
        subscriptions.forEach(this::fetchOne);
    }

    private void fetchOne(String rateName) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/" + rateName))
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(3))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            JsonNode jsonNode = objectMapper.readTree(resp.body());
            String name = jsonNode.path("rateName").asText(rateName);
            double bid = jsonNode.path("bid").asDouble();
            double ask = jsonNode.path("ask").asDouble();
            String ts = jsonNode.path("timestamp").asText(Instant.now().toString());

            RateFields rateFields = new RateFields(bid, ask, ts);

            if (notifiedRates.add(rateName)) {
                coordinator.onRateAvailable(platformName, name, new Rate(name, rateFields, new RateStatus(true,true)));
            }
            else {
                coordinator.onRateUpdate(platformName, rateName, rateFields);
            }
        } catch (Exception e) {
            LOG.warn("🌐 Failed to fetch REST data for [{}] → {}", rateName, e.getMessage());
        }
    }

    /* ═════ helpers ═════ */

    private boolean loadOwnConfig() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is == null) {
                LOG.error("{} not found", CONFIG_FILE);
                return false;
            }

            String txt = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            JSONObject cfg = new JSONObject(txt);

            this.baseUrl      = cfg.getString("restApiUrl");
            this.apiKey       = cfg.getString("apiKey");
            this.pollInterval = Duration.ofSeconds(cfg.optInt("pollInterval", 5));
            return true;

        } catch (Exception e) {
            LOG.error("Config load failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        notifiedRates.clear();
        connected.set(false);
        safe(() -> coordinator.onDisConnect(platformName, false));
    }

    private static void safe(Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            LOG.debug("safe-run error: {}", e.getMessage());
        }
    }
}
