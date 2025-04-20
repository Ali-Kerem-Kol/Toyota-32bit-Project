package com.mydomain.main.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydomain.main.coordinator.ICoordinator;
import com.mydomain.main.model.Rate;
import com.mydomain.main.model.RateFields;
import com.mydomain.main.model.RateStatus;
import com.mydomain.main.service.HttpService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.Proxy;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RESTProvider: TCPProvider benzeri sürekli yeniden bağlanma mekanizması.
 * Sunucu kapalıysa, her pollIntervalSeconds süresinde sunucu erişilebilirliğini test edip,
 * erişilemiyorsa tüm rate'lerin durumunu false yapar; erişilebilirse verileri çekip günceller.
 */
public class RESTProvider implements IProvider {

    private static final Logger logger = LogManager.getLogger(RESTProvider.class);

    private ICoordinator coordinator;
    private HttpService httpService;

    private String platformName;
    private String restApiUrl;
    private String apiKey;
    private int pollIntervalSeconds = 5;

    private Thread pollThread;
    private AtomicBoolean running = new AtomicBoolean(false);

    // Abone olunan rateName listesi
    private final Set<String> subscribedRates = new CopyOnWriteArraySet<>();

    // Jackson ObjectMapper => JSON parse
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RESTProvider() {
        // Reflection ile çağrılacak
    }

    @Override
    public void connect(String platformName, Map<String, String> params) {
        this.platformName = platformName;
        logger.info("✅ RESTProvider connect => {}, params: {}", platformName, params);

        if (coordinator != null) {
            coordinator.onConnect(platformName, true);
        }
        if (params.containsKey("restApiUrl")) {
            this.restApiUrl = params.get("restApiUrl");
        }
        if (params.containsKey("apiKey")) {
            this.apiKey = params.get("apiKey");
        }
        if (params.containsKey("pollInterval")) {
            this.pollIntervalSeconds = Integer.parseInt(params.get("pollInterval"));
        }

        // maxRetries ile ilgili kod tamamen kaldırıldı.
        Proxy proxy = null; // opsiyonel

        this.httpService = new HttpService(
                this.apiKey,
                null,
                null,
                true,   // useBearer
                false,  // useBasic
                proxy
        );

        startPollingThread();
    }

    @Override
    public void disConnect(String platformName, Map<String, String> params) {
        logger.info("✅ Disconnected from REST => {}", platformName);
        if (coordinator != null) {
            coordinator.onDisConnect(platformName, true);
        }
        running.set(false);
        if (pollThread != null) {
            pollThread.interrupt();
        }
        // Bağlantı kesildiğinde tüm aboneliklerin durumunu false yapıyoruz.
        updateRateStatusForAll(false);
    }

    @Override
    public void subscribe(String platformName, String rateName) {
        subscribedRates.add(rateName);
        logger.info("RESTProvider => Subscribed to {} on {}", rateName, platformName);
    }

    @Override
    public void unSubscribe(String platformName, String rateName) {
        subscribedRates.remove(rateName);
        logger.info("RESTProvider => Unsubscribed from {} on {}", rateName, platformName);
    }

    @Override
    public void setCoordinator(ICoordinator coordinator) {
        this.coordinator = coordinator;
    }

    /**
     * Polling döngüsü:
     * - Eğer subscribedRates boşsa bekler.
     * - İlk rate üzerinden sunucu erişilebilirliğini test eder;
     *   erişilemiyorsa uyarı verip tüm rate'lerin durumunu false yapar,
     *   erişilebilirse tüm rate'lerin durumunu true yapıp verileri çeker.
     */
    private void startPollingThread() {
        running.set(true);
        pollThread = new Thread(() -> {
            while (running.get()) {
                try {
                    if (subscribedRates.isEmpty()) {
                        logger.debug("No subscribed rates, waiting...");
                        Thread.sleep(pollIntervalSeconds * 1000L);
                        continue;
                    }

                    boolean serverAvailable = testServerAvailable(subscribedRates.iterator().next());
                    // Duruma göre tüm rate'lerin status'unu güncelle
                    updateRateStatusForAll(serverAvailable);

                    if (serverAvailable) {
                        for (String rateName : subscribedRates) {
                            fetchRateInternal(rateName);
                        }
                    } else {
                        logger.warn("REST server not reachable. Will retry in {} seconds...", pollIntervalSeconds);
                    }

                    Thread.sleep(pollIntervalSeconds * 1000L);
                } catch (InterruptedException ie) {
                    logger.warn("REST poll thread interrupted => stopping");
                    break;
                } catch (Exception e) {
                    logger.error("REST poll error => {}", e.getMessage(), e);
                }
            }
        }, "RestProviderPollThread-" + platformName);

        pollThread.setDaemon(true);
        pollThread.start();
    }

    /**
     * Test eder: belirtilen rate üzerinden sunucu erişilebilir mi?
     */
    private boolean testServerAvailable(String rateName) {
        try {
            if (restApiUrl == null) {
                logger.warn("No restApiUrl => cannot fetch => rateName={}", rateName);
                return false;
            }
            String url = restApiUrl + "/" + rateName;
            String jsonResponse = httpService.get(url);
            logger.info("Successfully fetched {} => server is up!", rateName);
            return true;
        } catch (Exception e) {
            logger.debug("Server check failed for rate={}, err={}", rateName, e.toString());
            return false;
        }
    }

    /**
     * Tüm subscribed rate'ler için, bağlantı durumuna göre RateStatus günceller.
     */
    private void updateRateStatusForAll(boolean active) {
        for (String rateName : subscribedRates) {
            if (coordinator != null) {
                // Eğer sunucu erişilemezse, isActive ve isUpdated false; erişilebilirse true
                RateStatus status = new RateStatus(active, active);
                coordinator.onRateStatus(platformName, rateName, status);
            }
        }
    }

    /**
     * Belirtilen rateName için REST sunucusundan veri çekip, Coordinator'a callback yapar.
     */
    private Rate fetchRateInternal(String rateName) {
        try {
            if (restApiUrl == null) {
                logger.warn("No restApiUrl => cannot fetch => rateName={}", rateName);
                return null;
            }
            String url = restApiUrl + "/" + rateName;
            String jsonResponse = httpService.get(url);

            JsonNode root = objectMapper.readTree(jsonResponse);
            String rName = root.path("rateName").asText(rateName);
            double bid = root.path("bid").asDouble(0.0);
            double ask = root.path("ask").asDouble(0.0);
            String isoTime = root.path("timestamp").asText(Instant.now().toString());

            RateFields fields = new RateFields(bid, ask, isoTime);
            // Varsayılan olarak sunucu erişilebilirse yeni rate için status true kabul ediyoruz.
            RateStatus status = new RateStatus(true, true);
            Rate rate = new Rate(rName, fields, status);

            if (coordinator != null) {
                coordinator.onRateAvailable(platformName, rName, rate);
                coordinator.onRateUpdate(platformName, rName, fields);
            }
            return rate;
        } catch (Exception e) {
            logger.warn("Error fetching REST for rate={} => {}", rateName, e.getMessage());
            logger.debug("Stacktrace:", e);
            return null;
        }
    }
}
