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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tek-iş parçacıklı (single-thread) TCPProvider.
 * AppConfig zaten her provider’ı kendi <ProviderThread-X>’inde koşturduğu için
 * connect() içinde ek thread oluşturulmaz; aynı thread bağlanır, okur,
 * bağlantı koparsa bekleyip yeniden bağlanır.
 *
 * RateStatus “aktif/pasif” mantığı kaldırılmıştır.  Redis-TTL tek otoritedir.
 */
public class TCPProvider implements IProvider {

    private static final Logger log = LogManager.getLogger(TCPProvider.class);

    /* — DI — */
    private ICoordinator coordinator;

    /* — config — */
    private String platformName;
    private String host;
    private int    port;

    /* — runtime — */
    private Socket         socket;
    private BufferedReader reader;
    private OutputStream   writer;

    private final AtomicBoolean running       = new AtomicBoolean(false);
    private final AtomicBoolean autoReconnect = new AtomicBoolean(true);

    private final Set<String> subscriptions = new CopyOnWriteArraySet<>();

    private final Set<String> sentOnce = ConcurrentHashMap.newKeySet();


    public TCPProvider() {
        // Reflection ile çağrılacak
    }


    /* — IProvider — ------------------------------------------------------------ */

    @Override
    public void connect(String platform, Map<String, String> params) {
        platformName = platform;
        host = params.get("host");
        port = Integer.parseInt(params.get("port"));

        while (autoReconnect.get()) {
            try {
                openSocket();

                if (coordinator != null) coordinator.onConnect(platformName, true);
                log.info("🟢 TCP connected {}", platformName);

                // önceki abonelikleri yeniden gönder
                for (String r : subscriptions) sendCmd("subscribe|" + r);

                // BLOKLU okuma döngüsü (tek thread)
                String line;
                while (running.get() && (line = reader.readLine()) != null) {
                    handleLine(line);
                }

                log.warn("Server closed connection");

            } catch (Exception e) {
                log.error("TCP error => {}", e.getMessage());
            } finally {
                closeSocket();
            }

            if (!autoReconnect.get()) break;
            log.info("Reconnecting in 5 s…");
            sleepQuiet(5_000);
        }
    }
    @Override
    public void disConnect(String platform, Map<String, String> p) {
        autoReconnect.set(false);
        subscriptions.clear();
        sentOnce.clear(); // abonelikten çıkınca sil
        closeSocket();
        if (coordinator != null) coordinator.onDisConnect(platformName, false);
    }
    @Override
    public void subscribe  (String plat, String rate) {
        subscriptions.add(rate); sendCmd("subscribe|"   + rate);
    }
    @Override
    public void unSubscribe(String plat, String rate) {
        subscriptions.remove(rate);
        sendCmd("unsubscribe|" + rate);
        sentOnce.remove(rate); // abonelikten çıkınca sil
    }
    @Override
    public void setCoordinator(ICoordinator c) {
        coordinator = c;
    }

    /* — socket helpers — ------------------------------------------------------- */

    private void openSocket() throws Exception {
        log.info("🔄 TCP connecting {}:{}", host, port);
        socket = new Socket(host, port);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = socket.getOutputStream();
        running.set(true);
    }

    private void closeSocket() {
        running.set(false);
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {

        }
    }

    private void sendCmd(String cmd) {
        try {
            if (!running.get()) return;
            writer.write((cmd + "\n").getBytes());
            writer.flush();
            log.debug("➡️  {}", cmd.trim());
        } catch (Exception e) {
            log.warn("sendCmd err {} => {}", cmd.trim(), e.getMessage());
        }
    }

    /* — parsing — -------------------------------------------------------------- */

    private void handleLine(String line) {
        if (!line.contains("|")) return;                 // control msg

        String[] p = line.split("\\|");
        if (p.length < 4) { log.warn("Bad TCP msg {}", line); return; }

        String rateName = p[0];
        double bid      = Double.parseDouble(p[1].split(":",3)[2]);
        double ask      = Double.parseDouble(p[2].split(":",3)[2]);
        long   ts       = parseTs(p[3]);

        if (coordinator == null) return;

        RateFields fields = new RateFields(bid, ask, ts);
        if (sentOnce.add(rateName)) {
            Rate r = new Rate(rateName, fields, new RateStatus(true, true));
            coordinator.onRateAvailable(platformName, rateName, r);
        } else {
            coordinator.onRateUpdate(platformName, rateName, fields);
        }
    }

    private long parseTs(String seg) {
        String dateStr = seg.split(":",3)[2];
        var fmt = new DateTimeFormatterBuilder()
                .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
                .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND,1,9,true).optionalEnd()
                .appendPattern("[XXX][X]")
                .toFormatter();
        return OffsetDateTime.parse(dateStr, fmt).toInstant().toEpochMilli();
    }

    private void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

}
