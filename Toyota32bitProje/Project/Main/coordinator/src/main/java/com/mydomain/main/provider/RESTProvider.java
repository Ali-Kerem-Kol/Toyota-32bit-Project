package com.mydomain.main.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydomain.main.cache.RateCache;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class RESTProvider implements IProvider, AutoCloseable {

    private static final Logger logger = LogManager.getLogger(RESTProvider.class);
    private static final String CONFIG_FILE_PATH = "/app/Main/coordinator/config/rest-config.json";

    private ICoordinator coordinator;
    private String platformName;
    private RateCache cache;

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

    @Override
    public void connect(String platformName, Map<String, String> _ignored) {
        this.platformName = platformName;
        logger.trace("connect() called for platform: {}", platformName);

        if (!loadOwnConfig()) {
            logger.error("⛔ Config load failed – RESTProvider could not be started.");
            return;
        }

        logger.info("🔍 [{}] REST config loaded: url={}, interval={}s",
                platformName, baseUrl, pollInterval.getSeconds());

        connected.set(true);
        logger.trace("Calling coordinator.onConnect() for: {}", platformName);
        safe(() -> coordinator.onConnect(platformName, true));

        scheduler.scheduleAtFixedRate(this::pollAll, 0, pollInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void disConnect(String platformName, Map<String, String> _unused) {
        logger.trace("disConnect() called for platform: {}", platformName);
        close();
    }

    @Override
    public void subscribe(String platformName, String rateName) {
        subscriptions.add(rateName);
        logger.info("📡 [{}] Subscribed to rate: {}", platformName, rateName);
    }

    @Override
    public void unSubscribe(String platformName, String rateName) {
        subscriptions.remove(rateName);
        logger.info("📴 [{}] Unsubscribed from rate: {}", platformName, rateName);
    }

    @Override
    public void setCoordinator(ICoordinator c) {
        this.coordinator = c;
        logger.trace("Coordinator reference set for RESTProvider.");
    }

    @Override
    public void setCache(RateCache cache) {
        this.cache = cache;
        logger.trace("Cache reference set for RESTProvider.");
    }

    private void pollAll() {
        if (subscriptions.isEmpty()) {
            logger.debug("[{}] No subscriptions to poll.", platformName);
            return;
        }
        if (baseUrl == null) {
            logger.error("[{}] Base URL not set. Cannot poll.", platformName);
            return;
        }

        logger.trace("[{}] Starting poll for subscriptions: {}", platformName, subscriptions);
        subscriptions.forEach(this::fetchOne);
    }

    private void fetchOne(String rateName) {
        try {
            logger.trace("[{}] Fetching data for rate: {}", platformName, rateName);

            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/" + rateName))
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(3))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            JsonNode jsonNode = objectMapper.readTree(resp.body());
            rateName = jsonNode.path("rateName").asText();
            double bid = jsonNode.path("bid").asDouble();
            double ask = jsonNode.path("ask").asDouble();
            String ts = jsonNode.path("timestamp").asText(Instant.now().toString());

            logger.trace("[{}] Fetched REST rate: {} = bid:{} ask:{} ts:{}",
                    platformName, rateName, bid, ask, ts);

            if (cache.isFirstRate(platformName, rateName)) {
                Rate rate = cache.addNewRate(platformName, rateName, new RateFields(bid, ask, ts));
                logger.debug("[{}] First rate for {} → sending to coordinator.onRateAvailable()", platformName, rateName);
                coordinator.onRateAvailable(platformName, rateName, rate);
            } else {
                Rate updatedRate = cache.updateRate(platformName, rateName, new RateFields(bid, ask, ts));
                if (updatedRate == null) {
                    logger.warn("[{}] ⚠️ Rate rejected by filters: {}", platformName, rateName);
                    return;
                }
                logger.debug("[{}] Updated rate for {} → sending to coordinator.onRateUpdate()", platformName, rateName);
                coordinator.onRateUpdate(platformName, rateName, updatedRate.getFields());
            }

        } catch (Exception e) {
            logger.error("🌐 [{}] Failed to fetch/process REST data for [{}] → {}", platformName, rateName, e.getMessage(), e);
        }
    }

    private boolean loadOwnConfig() {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(CONFIG_FILE_PATH));
            String text = new String(bytes, StandardCharsets.UTF_8);
            JSONObject cfg = new JSONObject(text);

            this.baseUrl = cfg.getString("restApiUrl");
            this.apiKey = cfg.getString("apiKey");
            this.pollInterval = Duration.ofSeconds(cfg.optInt("pollInterval", 5));

            logger.trace("REST config parsed: url={}, interval={}s", baseUrl, pollInterval.getSeconds());
            return true;

        } catch (Exception e) {
            logger.error("Config load failed from path [{}]: {}", CONFIG_FILE_PATH, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        connected.set(false);
        logger.trace("Calling coordinator.onDisConnect() for: {}", platformName);
        safe(() -> coordinator.onDisConnect(platformName, false));
        logger.info("RESTProvider shut down for platform: {}", platformName);
    }

    private static void safe(Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            logger.debug("safe-run error: {}", e.getMessage(), e);
        }
    }
}
