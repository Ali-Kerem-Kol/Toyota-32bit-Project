package com.mydomain.main.provider;

import com.mydomain.main.coordinator.ICoordinator;
import com.mydomain.main.model.Rate;
import com.mydomain.main.model.RateFields;
import com.mydomain.main.model.RateStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class TCPProvider implements IProvider {

    private static final Logger logger = LogManager.getLogger(TCPProvider.class);

    private ICoordinator coordinator;
    private String platformName;
    private String host;
    private int port;

    private Socket socket;
    private BufferedReader reader;
    private OutputStream writer;

    // Bağlantı durumunu takip eder.
    private volatile boolean running = false;
    private volatile boolean autoReconnect = true;

    private final Set<String> subscriptions = Collections.synchronizedSet(new HashSet<>());

    public TCPProvider() {
        // Reflection kullanımı için
    }

    @Override
    public void connect(String platformName, Map<String, String> params) {
        this.platformName = platformName;
        autoReconnect = true; // Bağlantı girişimlerinde otomatik yeniden bağlanma aktif

        if (params.containsKey("host")) {
            this.host = params.get("host");
        }
        if (params.containsKey("port")) {
            this.port = Integer.parseInt(params.get("port"));
        }

        try {
            logger.info("🔄 TCP connecting to {}:{}", host, port);
            socket = new Socket(host, port);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = socket.getOutputStream();
            running = true;
            // Bağlantı başarılı olduğunda
            if (coordinator != null) {
                coordinator.onConnect(platformName, true);
            }
            logger.info("🟢 TCP connected => {}", platformName);
            listenForData();
        } catch (Exception e) {
            logger.error("❌ TCP connect error => {}", e.getMessage());
            running = false;
            // Bağlantı kurulamadıysa abonelikteki tüm rate'lerin durumunu güncelle
            updateRateStatusForAll(false);
            if (autoReconnect) {
                reconnect();
            }
        }
    }

    @Override
    public void disConnect(String platformName, Map<String, String> params) {
        autoReconnect = false; // Manuel disconnect durumunda yeniden bağlanmayı kapat
        running = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            if (coordinator != null) {
                coordinator.onDisConnect(platformName, true);
            }
            updateRateStatusForAll(false);
        } catch (Exception e) {
            logger.error("❌ TCP disconnect error => {}", e.getMessage(), e);
        }
    }

    @Override
    public void subscribe(String platformName, String rateName) {
        try {
            if (!running) {
                throw new IllegalStateException("Not connected to TCP server.");
            }
            String cmd = "subscribe|" + rateName + "\n";
            writer.write(cmd.getBytes());
            writer.flush();
            subscriptions.add(rateName);
            logger.info("✅ Subscribed to {} on {}", rateName, platformName);
        } catch (Exception e) {
            logger.error("❌ subscribe error => {}", e.getMessage(), e);
        }
    }

    @Override
    public void unSubscribe(String platformName, String rateName) {
        try {
            if (!subscriptions.contains(rateName)) {
                logger.warn("⚠️ Not subscribed to {}", rateName);
                return;
            }
            String cmd = "unsubscribe|" + rateName + "\n";
            writer.write(cmd.getBytes());
            writer.flush();
            subscriptions.remove(rateName);
            logger.info("✅ Unsubscribed from {} on {}", rateName, platformName);
        } catch (Exception e) {
            logger.error("❌ unSubscribe error => {}", e.getMessage(), e);
        }
    }

    @Override
    public void setCoordinator(ICoordinator coordinator) {
        this.coordinator = coordinator;
    }

    private void listenForData() {
        new Thread(() -> {
            while (running) {
                try {
                    String line = reader.readLine();
                    if (line == null) {
                        logger.warn("TCP read null => server might be closed");
                        break;
                    }
                    logger.info("📩 TCP Received: {}", line);
                    parseAndCallback(line);
                } catch (Exception e) {
                    logger.error("❌ TCP read error => {}", e.getMessage(), e);
                    break;
                }
            }
            // Bağlantı kesildiğinde yeniden bağlanmayı dene ve durum güncellemesi yap
            if (autoReconnect) {
                updateRateStatusForAll(false);
                logger.info("Connection lost. Attempting to reconnect...");
                reconnect();
            }
        }, "TcpProviderListener-" + platformName).start();
    }

    private void reconnect() {
        running = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception e) {
            logger.error("Error closing socket during reconnect: {}", e.getMessage(), e);
        }
        while (autoReconnect && !running) {
            try {
                logger.info("🔄 Reconnecting to {}:{}", host, port);
                socket = new Socket(host, port);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                writer = socket.getOutputStream();
                running = true;
                logger.info("🟢 Reconnected to {}:{}", host, port);
                if (coordinator != null) {
                    coordinator.onConnect(platformName, true);
                }
                // Abone olunan rate'lar için yeniden abone ol
                for (String rateName : subscriptions) {
                    subscribe(platformName, rateName);
                }
                // Bağlantı yeniden sağlandığında tüm rate'lerin durumunu true yap
                updateRateStatusForAll(true);
                listenForData();
            } catch (Exception e) {
                logger.error("Reconnect attempt failed: {}", e.getMessage());
                try {
                    Thread.sleep(5000); // 5 saniye bekle
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void updateRateStatusForAll(boolean value) {
        // Abonelikteki tüm rate'ler için RateStatus bilgisini güncelle
        for (String rateName : subscriptions) {
            if (coordinator != null) {
                // Eğer bağlantı aktif değilse, isActive ve isUpdated false; aktifse true
                RateStatus status = new RateStatus(value, value);
                coordinator.onRateStatus(platformName, rateName, status);
            }
        }
    }

    private void parseAndCallback(String line) {
        if (!line.contains("|")) {
            logger.debug("Possibly a control message => {}", line);
            return;
        }
        String[] parts = line.split("\\|");
        if (parts.length < 4) {
            logger.warn("Invalid TCP message => {}", line);
            return;
        }
        String rateName = parts[0];
        double bid = parseNumberSegment(parts[1]);
        double ask = parseNumberSegment(parts[2]);
        long ts = parseTimestampSegment(parts[3]);

        RateFields fields = new RateFields(bid, ask, ts);
        // Yeni rate oluşturulurken varsayılan olarak bağlantının aktif olduğunu kabul ediyoruz.
        RateStatus status = new RateStatus(true, true);
        Rate rate = new Rate(rateName, fields, status);

        if (coordinator != null) {
            coordinator.onRateAvailable(platformName, rateName, rate);
            coordinator.onRateUpdate(platformName, rateName, fields);
        }
    }

    private double parseNumberSegment(String seg) {
        String[] sub = seg.split(":", 3);
        return Double.parseDouble(sub[2]);
    }

    private long parseTimestampSegment(String seg) {
        String[] sub = seg.split(":", 3);
        String dateStr = sub[2];
        var dtf = new DateTimeFormatterBuilder()
                .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
                .optionalStart()
                .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
                .optionalEnd()
                .appendPattern("[XXX][X]")
                .toFormatter();
        var odt = OffsetDateTime.parse(dateStr, dtf);
        return odt.toInstant().toEpochMilli();
    }
}
