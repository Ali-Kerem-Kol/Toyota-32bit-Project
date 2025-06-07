package com.mydomain.main.provider;

import com.mydomain.main.coordinator.ICoordinator;
import com.mydomain.main.exception.RedisException;
import com.mydomain.main.model.*;
import com.mydomain.main.redis.RedisService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class TCPProvider implements IProvider {

    private static final Logger log = LogManager.getLogger(TCPProvider.class);
    private static final String CONFIG_FILE_PATH = "/app/Main/coordinator/config/tcp-config.json";

    private ICoordinator coordinator;
    private String platformName;
    private RedisService redisService;

    private String host;
    private int port;

    private final AtomicBoolean reconnect = new AtomicBoolean(true);
    private volatile boolean running = false;

    private final Set<String> subscriptions = ConcurrentHashMap.newKeySet();
    private volatile BufferedReader in;
    private volatile OutputStream out;
    private Thread connectionThread;

    @Override
    public void connect(String platformName, Map<String, String> _ignored) {
        this.platformName = platformName;
        log.trace("connect() called for platform: {}", platformName);

        if (!loadOwnConfig()) {
            log.error("⛔ Config load failed – TCPProvider could not be started.");
            return;
        }

        log.info("🔍 [{}] TCP config loaded: host={}, port={}", platformName, host, port);

        connectionThread = new Thread(this::loop, "tcp-worker-" + platformName);
        connectionThread.setDaemon(true);
        connectionThread.start();
    }

    @Override
    public void disConnect(String platformName, Map<String, String> _unused) {
        log.trace("disConnect() called for platform: {}", platformName);
        reconnect.set(false);
        running = false;
        subscriptions.clear();
        log.trace("Calling coordinator.onDisConnect() for: {}", platformName);
        safe(() -> coordinator.onDisConnect(platformName, false));
        if (connectionThread != null) connectionThread.interrupt();
        log.debug("TCPProvider thread interrupted for: {}", platformName);
    }

    @Override
    public void subscribe(String platformName, String rate) {
        subscriptions.add(rate);
        log.trace("[{}] Subscribing to: {}", platformName, rate);

        if (running && out != null) {
            if (sendCmd("subscribe|" + rate))
                log.info("✅ [{}] Subscribed to rate: {}", platformName, rate);
            else
                log.warn("⚠️ [{}] Failed to subscribe to rate: {}", platformName, rate);
        } else {
            log.trace("[{}] Deferred subscribe for {} (no active connection)", platformName, rate);
        }
    }

    @Override
    public void unSubscribe(String platformName, String rate) {
        if (subscriptions.remove(rate)) {
            if (running && out != null) {
                log.trace("[{}] Sending unsubscribe command for rate: {}", platformName, rate);
                sendCmd("unsubscribe|" + rate);
            }
            log.info("✅ [{}] Unsubscribed from rate: {}", platformName, rate);
        }
    }

    @Override
    public void setCoordinator(ICoordinator c) {
        this.coordinator = c;
        log.trace("Coordinator reference set for TCPProvider.");
    }

    @Override
    public void setRedis(RedisService redisService) {
        this.redisService = redisService;
    }

    private void loop() {
        log.trace("🔁 [{}] TCP loop started", platformName);
        while (reconnect.get()) {
            try (Socket socket = new Socket(host, port);
                 BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 OutputStream output = socket.getOutputStream()) {

                this.in = input;
                this.out = output;
                running = true;
                log.trace("Calling coordinator.onConnect() for: {}", platformName);
                safe(() -> coordinator.onConnect(platformName, true));

                log.info("🔌 [{}] TCP connection established with {}:{}", platformName, host, port);
                subscriptions.forEach(this::sendSilently);

                String line;
                while (running && (line = in.readLine()) != null) handle(line);

            } catch (IOException ioe) {
                log.error("❗ [{}] TCP connect/read failed {}:{} → {}. Retrying in 5s...", platformName, host, port, ioe.getMessage(), ioe);
            }

            running = false;
            log.trace("Calling coordinator.onDisConnect() for: {}", platformName);
            safe(() -> coordinator.onDisConnect(platformName, false));
            log.info("🔌 [{}] TCP connection closed", platformName);

            if (reconnect.get()) waitMs(5000);
        }
        log.trace("🔁 [{}] TCP loop terminated", platformName);
    }

    private boolean loadOwnConfig() {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(CONFIG_FILE_PATH));
            String json = new String(bytes, StandardCharsets.UTF_8);
            JSONObject cfg = new JSONObject(json);
            this.host = cfg.getString("host");
            this.port = cfg.getInt("port");
            return true;
        } catch (Exception e) {
            log.error("Config load failed from path [{}]: {}", CONFIG_FILE_PATH, e.getMessage(), e);
            return false;
        }
    }

    private void sendSilently(String rate) {
        log.trace("[{}] Silently sending subscription command for: {}", platformName, rate);
        if (out != null) sendCmd("subscribe|" + rate);
    }

    private boolean sendCmd(String cmd) {
        try {
            log.trace("[{}] Sending TCP command: {}", platformName, cmd);
            out.write((cmd + '\n').getBytes());
            out.flush();
            return true;
        } catch (Exception e) {
            log.error("📤 [{}] Failed to send command [{}] → {}", platformName, cmd, e.getMessage(), e);
            return false;
        }
    }

    private void handle(String line) {
        try {
            if (!line.contains("|") || coordinator == null) return;

            String[] p = line.split("\\|");

            if (p.length < 4) {
                log.warn("🚫 [{}] Invalid TCP message: {}", platformName, line);
                return;
            }

            String rateName = p[0];
            double bid = Double.parseDouble(p[1].split(":", 3)[2]);
            double ask = Double.parseDouble(p[2].split(":", 3)[2]);
            long ts = parseTimestamp(p[3]);

            log.trace("[{}] Received TCP data: {} = bid:{} ask:{} ts:{}", platformName, rateName, bid, ask, ts);


            Rate rate = new Rate(
                    rateName,
                    new RateFields(bid, ask, ts),
                    new RateStatus(true, false)
            );

            try {
                int result = redisService.putRawRate(platformName,rateName,rate);

                if (result == 0) {
                    coordinator.onRateAvailable(platformName, rateName, rate);
                } else if (result == 1) {
                    coordinator.onRateUpdate(platformName, rateName, rate.getFields());
                }
            } catch (RedisException e) {
                log.error("❌ [{}] Redis error while processing rate [{}]: {}", platformName, rateName, e.getMessage(), e);
            }
        } catch (Exception e) {
            log.error("📉 [{}] Failed to parse/process TCP data [{}] → {}", platformName, line, e.getMessage(), e);
        }
    }

    private long parseTimestamp(String raw) {
        try {
            String d = raw.split(":", 3)[2];
            var fmt = new DateTimeFormatterBuilder().appendPattern("yyyy-MM-dd'T'HH:mm:ss")
                    .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true).optionalEnd()
                    .appendPattern("[XXX][X]").toFormatter();
            long parsedTime = OffsetDateTime.parse(d, fmt).toInstant().toEpochMilli();
            log.trace("[{}] Parsed timestamp '{}' → {}", platformName, raw, parsedTime);
            return parsedTime;
        } catch (Exception e) {
            log.warn("[{}] Failed to parse timestamp [{}], using current time. Reason: {}", platformName, raw, e.getMessage());
            return System.currentTimeMillis();
        }
    }

    private static void safe(Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            log.debug("safe-run error: {}", e.getMessage(), e);
        }
    }

    private static void waitMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
