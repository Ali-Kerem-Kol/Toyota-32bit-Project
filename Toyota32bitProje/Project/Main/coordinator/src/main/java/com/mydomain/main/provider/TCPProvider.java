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

/**
 * {@code TCPProvider}, TCP tabanlı veri kaynaklarından kur (rate) verilerini çeker,
 * abonelik bazında veri akışını yönetir ve alınan verileri `RedisService`’e kaydeder.
 * `IProvider` arayüzünü uygulayarak platform bağlantılarını yönetir ve `ICoordinator` ile
 * veri bildirimlerini koordine eder. Bu sınıf, yeniden bağlanma (reconnect) mekanizması
 * ile kesintilere karşı dayanıklıdır ve thread-safe bir şekilde çalışır.
 *
 * <p>Hizmetin temel işleyişi:
 * <ul>
 *   <li>Konfigürasyon dosyasından (tcp-config.json) host ve port bilgilerini yükler.</li>
 *   <li>Abonelikler (`subscriptions`) bir `ConcurrentHashMap.newKeySet` ile thread-safe şekilde saklanır.</li>
 *   <li>TCP soketi üzerinden veri akışını dinler ve gelen verileri işler.</li>
 *   <li>Bağlantı kesilirse 5 saniye bekleyerek yeniden bağlanma denemesi yapar.</li>
 * </ul>
 * </p>
 *
 * <p><b>Özellikler:</b>
 * <ul>
 *   <li>Yeniden bağlanma (`reconnect`) özelliği ile kesintisiz çalışma sağlar.</li>
 *   <li>Loglama için Apache Log4j ile hata ayıklama ve izleme seviyeleri desteklenir.</li>
 *   <li>Veri işleme sırasında zaman damgası ayrıştırması esnek bir formatta yapılır.</li>
 * </ul>
 * </p>
 *
 * @author Ali Kerem Kol
 * @version 1.0
 * @since 2025-06-07
 */
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

    /**
     * Belirtilen platform adına TCP bağlantısını kurar ve veri akışını başlatır.
     * Konfigürasyon dosyasını yükler, bağlantı thread’ini başlatır ve `ICoordinator`’a bildirim yapar.
     * Eğer konfigürasyon yüklenemezse bağlantı başarısız olur ve loglanır.
     *
     * @param platformName Bağlantı kurulacak platformun adı (örneğin "TCP_PLATFORM"),
     *                    null veya boş ise hata loglanır
     * @param _ignored Bağlantı parametreleri (bu uygulamada kullanılmaz, null olabilir)
     */
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

    /**
     * Belirtilen platform için TCP bağlantısını keser ve kaynakları serbest bırakır.
     * Yeniden bağlanma mekanizmasını durdurur, abonelikleri temizler ve bildirim yapar.
     *
     * @param platformName Bağlantısı kesilecek platformun adı,
     *                    null veya boş ise hata loglanır
     * @param _unused Bağlantı kesme parametreleri (bu uygulamada kullanılmaz, null olabilir)
     */
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

    /**
     * Belirtilen platformda bir kura (rate) abone olur.
     * Abonelik, `subscriptions` kümesine eklenir ve aktif bağlantı varsa komut gönderilir.
     *
     * @param platformName Abonelik yapılacak platformun adı,
     *                    null veya boş ise hata loglanır
     * @param rate Abone olunacak kurun adı (örneğin "USDTRY"),
     *             null veya boş ise hata loglanır
     */
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

    /**
     * Belirtilen platformda bir kura (rate) abonelikten çıkar.
     * Abonelik, `subscriptions` kümesinden kaldırılır ve aktif bağlantı varsa komut gönderilir.
     *
     * @param platformName Abonelikten çıkılacak platformun adı,
     *                    null veya boş ise hata loglanır
     * @param rate Aboneliği sonlandırılacak kurun adı,
     *             null veya boş ise hata loglanır
     */
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
        log.trace("Coordinator reference set for TCPProvider.");
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
     * TCP bağlantı döngüsünü yönetir.
     * Bağlantı kurulur, abonelik komutları sessizce gönderilir ve veri akışı dinlenir.
     * Bağlantı kesilirse 5 saniye bekleyerek yeniden bağlanma denemesi yapar.
     */
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

    /**
     * Kendi konfigürasyon dosyasını (tcp-config.json) yükler.
     * Host ve port parametrelerini parse eder.
     *
     * @return Konfigürasyon yükleme başarılıysa true, aksi halde false
     */
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

    /**
     * Belirtilen kur için abonelik komutunu sessizce gönderir.
     * Hata durumunda loglanır ancak ana akışı etkilemez.
     *
     * @param rate Abone olunacak kurun adı (örneğin "USDTRY"),
     *             null veya boş ise hata loglanır
     */
    private void sendSilently(String rate) {
        log.trace("[{}] Silently sending subscription command for: {}", platformName, rate);
        if (out != null) sendCmd("subscribe|" + rate);
    }

    /**
     * TCP soketine bir komut gönderir ve sonucunu döndürür.
     *
     * @param cmd Gönderilecek komut (örneğin "subscribe|USDTRY"),
     *            null veya boş ise hata loglanır
     * @return Komut başarıyla gönderildiyse true, aksi halde false
     */
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

    /**
     * Gelen TCP verisini işler ve Redis’e kaydeder.
     * Verinin geçerliliği kontrol edilir ve Coordinator’a bildirim yapılır.
     *
     * @param line İşlenecek TCP mesajı (örneğin "USDTRY|bid:1.23|ask:1.24|ts:2025-..."),
     *             null veya boş ise hata loglanır
     */
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

            int result = redisService.putRawRate(platformName, rateName, rate);

            if (result == 0) {
                coordinator.onRateAvailable(platformName, rateName, rate);
            } else if (result == 1) {
                coordinator.onRateUpdate(platformName, rateName, rate.getFields());
            } else if (result == -1) {
                log.warn("[{}] Filter rejected rate: {}", platformName, rateName);
            }
        } catch (NumberFormatException e) {
            log.error("📉 [{}] Failed to parse numeric data in [{}] → {}", platformName, line, e.getMessage());
        } catch (RedisException e) {
            log.error("❌ [{}] Redis error while processing rate [{}]: {}", platformName, line, e.getMessage());
        } catch (Exception e) {
            log.error("📉 [{}] Failed to parse/process TCP data [{}] → {}", platformName, line, e.getMessage());
        }
    }

    /**
     * Gelen ham zaman damgasını ayrıştırır ve epoch milisaniyesine dönüştürür.
     * Ayrıştırma başarısız olursa mevcut zaman kullanılır.
     *
     * @param raw Ayrıştırılacak ham zaman damgası (örneğin "ts:2025-06-07T05:40:00Z"),
     *            null veya boş ise hata loglanır
     * @return Epoch milisaniye cinsinden zaman damgası
     */
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

    /**
     * Belirtilen milisaniye kadar thread’i bekletir.
     * Kesilirse thread’in kesilme durumu korunur.
     *
     * @param ms Bekleme süresi (milisaniye cinsinden),
     *           0 veya negatifse hata loglanır ancak işlem devam eder
     */
    private static void waitMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
