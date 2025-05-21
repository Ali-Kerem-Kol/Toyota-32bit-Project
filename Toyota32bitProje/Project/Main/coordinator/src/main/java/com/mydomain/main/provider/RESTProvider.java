package com.mydomain.main.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydomain.main.coordinator.ICoordinator;
import com.mydomain.main.model.Rate;
import com.mydomain.main.model.RateFields;
import com.mydomain.main.model.RateStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

public class RESTProvider implements IProvider, AutoCloseable {

    private static final Logger log = LogManager.getLogger(RESTProvider.class);

    private ICoordinator coordinator;

    private String platformName;
    private String baseUrl;
    private String apiKey;
    private Duration pollInterval = Duration.ofSeconds(5);

    private final Set<String> subs = new CopyOnWriteArraySet<>();

    private final Set<String> sentOnce = ConcurrentHashMap.newKeySet();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "rest-poller");
                t.setDaemon(true);
                return t;
            });

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private final ObjectMapper om = new ObjectMapper();

    public RESTProvider() {
        // Reflection ile çağrılacak
    }


    @Override
    public void connect(String platform, Map<String, String> p) {
        this.platformName = platform;
        this.baseUrl      = p.get("restApiUrl");
        this.apiKey       = p.get("apiKey");
        if (p.containsKey("pollInterval"))
            pollInterval = Duration.ofSeconds(Integer.parseInt(p.get("pollInterval")));

        scheduler.scheduleAtFixedRate(this::pollAll, 0,
                pollInterval.toMillis(), TimeUnit.MILLISECONDS);

        if (coordinator != null) coordinator.onConnect(platformName, true);
        //log.info("✅ RESTProvider started for {} – interval {} s", platform, pollInterval.getSeconds());
    }

    @Override
    public void disConnect(String p, Map<String,String> pm) {
        close();
    }

    @Override
    public void subscribe  (String p, String r) {
        subs.add(r);
    }

    @Override
    public void unSubscribe(String p, String r) {
        subs.remove(r);
        sentOnce.remove(r); // unsubscribe edilince sıfırla
    }

    @Override
    public void setCoordinator(ICoordinator c)  {
        coordinator = c;
    }



    private void pollAll() {
        if (subs.isEmpty() || baseUrl == null) return;

        subs.parallelStream()
                .forEach(this::fetchOne);
    }

    private void fetchOne(String rateName) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/" + rateName))
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(3))
                    .build();

            String body = http.send(req, HttpResponse.BodyHandlers.ofString()).body();
            Rate r = parseRate(rateName, body);

            if (r == null || coordinator == null) return;

            if (sentOnce.add(rateName)) {
                coordinator.onRateAvailable(platformName, r.getRateName(), r);
            } else {
                coordinator.onRateUpdate(platformName, r.getRateName(), r.getFields());
            }

        } catch (Exception e) {
            log.warn("REST fetch error {} => {}", rateName, e.getMessage());
        }
    }

    private Rate parseRate(String fallback, String json) {
        try {
            JsonNode n  = om.readTree(json);
            String rN   = n.path("rateName").asText(fallback);
            double bid  = n.path("bid").asDouble();
            double ask  = n.path("ask").asDouble();
            String iso  = n.path("timestamp").asText(Instant.now().toString());

            return new Rate(rN, new RateFields(bid, ask, iso),
                    new RateStatus(true, true)); // status her zaman true/true
        } catch (Exception e) {
            log.error("JSON parse error {} => {}", fallback, e.getMessage());
            return null;
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        sentOnce.clear(); // tüm kayıtları temizle
        if (coordinator != null) coordinator.onDisConnect(platformName, false);
        //log.info("🛑 RESTProvider stopped for {}", platformName);
    }
}
