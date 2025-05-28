package com.mydomain.main.provider;

import com.mydomain.main.coordinator.ICoordinator;
import com.mydomain.main.model.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class TCPProvider implements IProvider {

    /* ───────── static ───────── */
    private static final Logger LOG = LogManager.getLogger(TCPProvider.class);
    private static final String CONFIG_FILE = "tcp-config.json";

    /* ───────── wiring ───────── */
    private ICoordinator coordinator;
    private String platform;

    /* bağlantı parametreleri */
    private String host;
    private int    port;

    /* ───────── state ───────── */
    private final AtomicBoolean reconnect = new AtomicBoolean(true);
    private volatile boolean    running   = false;

    private final Set<String> subscriptions = ConcurrentHashMap.newKeySet();
    private final Set<String> sentOnce      = ConcurrentHashMap.newKeySet();

    private volatile BufferedReader in;
    private volatile OutputStream   out;
    private Thread connectionThread;

    /* ═════ IProvider ═════ */

    @Override
    public void connect(String platform, Map<String, String> _ignored) {

        this.platform = platform;

        /* 1️⃣ bağlantı konfigürasyonunu yükle */
        if (!loadOwnConfig()) {
            LOG.error("⛔ Config load failed – TCPProvider could not be started.");
            return;
        }
        LOG.info("🔍 TCP config  host={} port={}", host, port);

        /* 2️⃣ bağlantı döngüsünü başlat */
        connectionThread = new Thread(this::loop, "tcp-worker-" + platform);
        connectionThread.setDaemon(true);
        connectionThread.start();
    }

    @Override
    public void disConnect(String _plat, Map<String, String> _unused) {
        reconnect.set(false);
        running = false;
        subscriptions.clear();
        sentOnce.clear();
        safe(() -> coordinator.onDisConnect(platform, false));
        if (connectionThread != null) connectionThread.interrupt();
    }

    @Override
    public void subscribe(String _plat, String rate) {
        /* her durumda set'e ekle */
        subscriptions.add(rate);

        if (running && out != null) {
            if (sendCmd("subscribe|" + rate))
                LOG.info("✅ Subscribed {}", rate);
            else
                LOG.warn("⚠️ Subscribe failed {}", rate);
        } else {
            LOG.info("🔒 No connection, will subscribe on reconnect: {}", rate);
        }
    }


    @Override
    public void unSubscribe(String _plat, String rate) {
        if (subscriptions.remove(rate)) {
            if (running && out != null) {
                sendCmd("unsubscribe|" + rate);
            }
            LOG.info("✅ Unsubscribed {}", rate);
        }
        //sentOnce.remove(rate); // RESTProvider daki mantık aynı şekilde burda da geçerli,bunu silmeli miyim acaba ??
    }

    @Override
    public void setCoordinator(ICoordinator c) { this.coordinator = c; }

    /* ═════ core loop ═════ */


    /**
     * Bu metod, TCP bağlantısını kurar ve bağlantı aktif olduğu sürece gelen verileri dinler.
     * Java'nın "try-with-resources" yapısı kullanılarak `Socket`, `BufferedReader` ve `OutputStream`
     * gibi dış kaynaklar otomatik olarak kapatılır. Bu, manuel `close()` çağrısı ihtiyacını ortadan kaldırır
     * ve kaynak sızıntılarını önler.
     *
     * Bağlantı sırasında oluşturulan `input` ve `output` nesneleri, sınıfın diğer metodlarında
     * da erişilebilmesi amacıyla `this.in` ve `this.out` alanlarına atanır. Böylece,
     * örneğin `sendCmd()` gibi metodlar bu bağlantı üzerinden veri gönderebilir.
     *
     * Bağlantı koptuğunda otomatik olarak yeniden bağlanmayı dener; her döngü sonunda
     * mevcut abonelikler ve durum güncellenir.
     */
    private void loop() {
        while (reconnect.get()) {
            try (Socket socket = new Socket(host, port);
                 BufferedReader input  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 OutputStream   output = socket.getOutputStream()) {

                this.in = input; this.out = output; running = true;
                safe(() -> coordinator.onConnect(platform, true));

                /* mevcut tüm abonelikleri gönder */
                subscriptions.forEach(this::sendSilently);

                String line;
                while (running && (line = in.readLine()) != null) handle(line);

            } catch (IOException ioe) {
                LOG.warn("TCP connect/read failed {}:{} → {}. Will retry in 5 seconds.", host, port, ioe.getMessage());
            }
            running = false;
            sentOnce.clear();   //  ← eklendi !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            safe(() -> coordinator.onDisConnect(platform, false));
            if (reconnect.get()) waitMs(5_000);
        }
    }

    /* ───────── helpers ───────── */

    /** tcp-config.json dosyasını classpath'ten okur. */
    private boolean loadOwnConfig() {
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE);
            if (is == null) {
                LOG.error("{} not found", CONFIG_FILE);
                return false;
            }

            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject cfg = new JSONObject(json);
            this.host = cfg.getString("host");
            this.port = cfg.getInt("port");
            return true;

        } catch (Exception e) {
            LOG.error("Config load failed: {}", e.getMessage());
            return false;
        }
    }

    private void sendSilently(String rate) {
        if (out != null) sendCmd("subscribe|" + rate);
    }

    private boolean sendCmd(String cmd) {
        try {
            out.write((cmd + '\n').getBytes());
            out.flush();
            return true;
        }
        catch (Exception e) {
            LOG.error("📤 Send err [{}] {}", cmd, e.getMessage()); return false;
        }
    }

    private void handle(String line) {
        if (!line.contains("|") || coordinator == null) return;

        String[] p = line.split("\\|");

        if (p.length < 4) {
            LOG.warn("🚫 Bad msg {}", line);
            return;
        }
        try {
            String name = p[0];
            double bid = Double.parseDouble(p[1].split(":",3)[2]);
            double ask = Double.parseDouble(p[2].split(":",3)[2]);
            long ts   = parseTimestamp(p[3]);


            RateFields rateFields = new RateFields(bid, ask, ts);

            if (sentOnce.add(name)) {
                coordinator.onRateAvailable(platform, name, new Rate(name, rateFields, new RateStatus(true,true)));
            }
            else {
                coordinator.onRateUpdate(platform, name, rateFields);
            }
        } catch (Exception e) {
            LOG.warn("📉 Parse err {}", e.getMessage());
        }
    }

    private long parseTimestamp(String raw) {
        try {
            String d = raw.split(":",3)[2];
            var fmt = new DateTimeFormatterBuilder().appendPattern("yyyy-MM-dd'T'HH:mm:ss")
                    .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND,1,9,true).optionalEnd()
                    .appendPattern("[XXX][X]").toFormatter();
            return OffsetDateTime.parse(d, fmt).toInstant().toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private static void safe(Runnable r) {
        try {
            r.run();
        } catch (Exception ignored) {

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
