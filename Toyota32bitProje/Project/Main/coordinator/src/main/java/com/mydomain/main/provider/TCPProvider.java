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
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TCPProvider sınıfı TCP sunucusuna bağlanarak gelen verileri dinler,
 * parse eder ve Coordinator üzerinden uygulamanın geri kalanına iletir.
 */
public class TCPProvider implements IProvider {

    private static final Logger logger = LogManager.getLogger(TCPProvider.class);

    private ICoordinator coordinator;
    private String platformName;
    private String host;
    private int port;

    private Socket socket;
    private BufferedReader reader;
    private OutputStream writer;

    /** Bağlantı durumu bayrağı (thread-safe) */
    private final AtomicBoolean running       = new AtomicBoolean(false);
    /** Otomatik yeniden bağlanma bayrağı (thread-safe) */
    private final AtomicBoolean autoReconnect = new AtomicBoolean(true);

    /** Abonelikler (lock-free okuma/yazma) */
    private final Set<String> subscriptions = new CopyOnWriteArraySet<>();
    /** Daha önce gördüğümüz rateName’ler (ilk geliş vs. güncelleme) */
    private final Set<String> seenRates     = new CopyOnWriteArraySet<>();

    public TCPProvider() {
        // Reflection kullanımı için boş yapıcı
    }

    /**
     * TCP sunucusuna bağlanır ve veri dinleme iş parçacığını başlatır.
     * Bağlantı başarılıysa Coordinator'a bilgi gönderir.
     *
     * @param platformName Platformun adı
     * @param params       Bağlantı parametreleri (host, port)
     */
    @Override
    public void connect(String platformName, Map<String, String> params) {
        this.platformName = platformName;
        this.autoReconnect.set(true);

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
            running.set(true);

            if (coordinator != null) {
                coordinator.onConnect(platformName, true);
            }
            logger.info("🟢 TCP connected => {}", platformName);
            listenForData();
        } catch (Exception e) {
            logger.error("❌ TCP connect error => {}", e.getMessage());
            running.set(false);
            updateRateStatusForAll(false);
            if (autoReconnect.get()) {
                reconnect();
            }
        }
    }

    /**
     * Mevcut TCP bağlantısını kapatır, yeniden bağlanmayı durdurur
     * ve Coordinator'a bağlantı kesildi bildiriminde bulunur.
     *
     * @param platformName Platform adı
     * @param params       Kullanılmayan parametreler
     */
    @Override
    public void disConnect(String platformName, Map<String, String> params) {
        autoReconnect.set(false);
        running.set(false);
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            if (coordinator != null) {
                coordinator.onDisConnect(platformName, true);
            }
            updateRateStatusForAll(false);
        } catch (Exception e) {
            logger.error("❌ TCP disconnect error => {}", e.getMessage());
        }
    }

    /**
     * Belirtilen kur için sunucuya subscribe komutu gönderir
     * ve local abonelik listesine ekler.
     *
     * @param platformName Platform adı
     * @param rateName     Abone olunacak kur adı
     */
    @Override
    public void subscribe(String platformName, String rateName) {
        try {
            if (!running.get()) {
                throw new IllegalStateException("Not connected to TCP server.");
            }
            String cmd = "subscribe|" + rateName + "\n";
            writer.write(cmd.getBytes());
            writer.flush();
            subscriptions.add(rateName);
            logger.info("✅ Subscribed to {} on {}", rateName, platformName);
        } catch (Exception e) {
            logger.error("❌ subscribe error => {}", e.getMessage());
        }
    }

    /**
     * Belirtilen kur için sunucuya unsubscribe komutu gönderir
     * ve local abonelik listeden çıkarır.
     *
     * @param platformName Platform adı
     * @param rateName     Abonelikten çıkarılacak kur adı
     */
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
            logger.error("❌ unSubscribe error => {}", e.getMessage());
        }
    }

    /**
     * Coordinator referansını atar. Sağlayıcı bu referans üzerinden callback yapar.
     *
     * @param coordinator Uygulama koordinatörü
     */
    @Override
    public void setCoordinator(ICoordinator coordinator) {
        this.coordinator = coordinator;
    }

    /**
     * Gelen TCP verilerini sürekli okuyan ve parse edip callback yapan iş parçacığını başlatır.
     */
    private void listenForData() {
        new Thread(() -> {
            while (running.get()) {
                try {
                    String line = reader.readLine();
                    if (line == null) {
                        logger.warn("TCP read null => server might be closed");
                        break;
                    }
                    logger.info("📩 TCP Received: {}", line);
                    parseAndCallback(line);
                } catch (Exception e) {
                    logger.error("❌ TCP read error => {}", e.getMessage());
                    break;
                }
            }
            if (autoReconnect.get()) {
                updateRateStatusForAll(false);
                logger.info("Connection lost. Attempting to reconnect...");
                reconnect();
            }
        }, "TcpProviderListener-" + platformName).start();
    }

    /**
     * Bağlantı koptuğunda tekrar bağlanmayı deneyen döngüyü çalıştırır.
     */
    private void reconnect() {
        running.set(false);
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception e) {
            logger.error("Error closing socket during reconnect: {}", e.getMessage());
        }
        while (autoReconnect.get() && !running.get()) {
            try {
                logger.info("🔄 Reconnecting to {}:{}", host, port);
                socket = new Socket(host, port);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                writer = socket.getOutputStream();
                running.set(true);
                logger.info("🟢 Reconnected to {}:{}", host, port);
                if (coordinator != null) {
                    coordinator.onConnect(platformName, true);
                }
                for (String rateName : subscriptions) {
                    subscribe(platformName, rateName);
                }
                updateRateStatusForAll(true);
                listenForData();
            } catch (Exception e) {
                logger.error("Reconnect attempt failed: {}", e.getMessage());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * Local abonelik listesindeki her kur için status bilgisini Coordinator'a günceller.
     *
     * @param value Bağlantı aktifse true, pasifse false
     */
    private void updateRateStatusForAll(boolean value) {
        if (coordinator == null) return;
        for (String rateName : subscriptions) {
            coordinator.onRateStatus(platformName, rateName, new RateStatus(value, value));
        }
    }

    /**
     * Gelen satırı parse edip Rate nesnesine dönüştürür ve Coordinator'a callback yapar.
     *
     * @param line TCP üzerinden gelen ham mesaj
     */
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
        RateStatus status = new RateStatus(true, true);
        Rate rate = new Rate(rateName, fields, status);

        if (coordinator != null) {
            // Eğer ilk defa bu rateName’i işliyorsak
            if (seenRates.add(rateName)) {
                coordinator.onRateAvailable(platformName, rateName, rate);
            } else {
                coordinator.onRateUpdate(platformName, rateName, fields);
            }
        }
    }

    /**
     * "22:number:34.123" formatındaki segmentten sayısal değeri çeker.
     *
     * @param seg Parçalanacak segment
     * @return Ayrıştırılan double değer
     */
    private double parseNumberSegment(String seg) {
        String[] sub = seg.split(":", 3);
        return Double.parseDouble(sub[2]);
    }

    /**
     * "5:timestamp:2025-04-22T15:06:16.306Z" formatındaki segmentten zamanı milisaniye cinsinden çeker.
     *
     * @param seg Parçalanacak segment
     * @return Epoch milisaniye değeri
     */
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
