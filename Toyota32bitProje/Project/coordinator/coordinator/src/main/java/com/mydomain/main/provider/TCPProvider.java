package com.mydomain.main.provider;

import com.mydomain.main.coordinator.CoordinatorInterface;
import com.mydomain.main.model.Rate;
import com.mydomain.main.model.RateFields;
import com.mydomain.main.model.RateStatus;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TCPProvider implements Provider {
    private final String host;
    private final int port;
    private Socket socket;
    private BufferedReader reader;
    private OutputStream writer;
    private final Set<String> subscriptions = new HashSet<>();
    private boolean isRunning = false;
    private CoordinatorInterface coordinator; // 🔹 Koordinatör referansı

    // Özel Formatter (fraction of second 1 ila 9 basamağı desteklesin, UTC offset'i parse etsin)
    private static final DateTimeFormatter CUSTOM_OFFSET_FORMATTER =
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
                    .optionalStart()
                    .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
                    .optionalEnd()
                    .appendPattern("[XXX][X]")  // "Z" veya "+00:00" vb. offset biçimlerini destekler
                    .toFormatter();


    public TCPProvider(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void setCoordinator(CoordinatorInterface coordinator) {
        this.coordinator = coordinator;
    }

    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public void connect(String platformName, String userId, String password) {
        try {
            System.out.println("🔄 TCP Bağlantısı Kuruluyor... " + host + ":" + port);
            socket = new Socket(host, port);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = socket.getOutputStream();
            isRunning = true;
            System.out.println("✅ Connected to TCP platform: " + platformName);

            if (coordinator != null) {
                coordinator.onConnect(platformName, true);
            }

            // Bağlantı doğrulaması
            if (socket.isConnected() && !socket.isClosed()) {
                System.out.println("🟢 TCP bağlantısı başarılı!");
            } else {
                System.out.println("🔴 TCP bağlantısı başarısız!");
            }

            // Gelen veriyi dinle
            listenForData();
        } catch (Exception e) {
            System.err.println("❌ Failed to connect to TCP server: " + e.getMessage());
            isRunning = false;
        }
    }

    @Override
    public void connect(String platformName, Map<String, String> params) {
        throw new UnsupportedOperationException("⚠️ TCP connection requires host and port.");
    }

    @Override
    public void disconnect(String platformName) {
        try {
            isRunning = false;
            if (socket != null && !socket.isClosed()) {
                socket.close();
                System.out.println("✅ Disconnected from TCP platform: " + platformName);
            }

            if (coordinator != null) {
                coordinator.onDisConnect(platformName, true);
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to disconnect: " + e.getMessage());
        }
    }

    @Override
    public void subscribe(String platformName, String rateName) {
        try {
            if (!isRunning || socket == null || socket.isClosed()) {
                throw new IllegalStateException("❌ Not connected to TCP server.");
            }
            String command = "subscribe|" + rateName + "\n";
            writer.write(command.getBytes());
            writer.flush();
            subscriptions.add(rateName);
            System.out.println("✅ Subscribed to " + rateName + " on " + platformName);
        } catch (Exception e) {
            System.err.println("❌ Failed to subscribe: " + e.getMessage());
        }
    }

    @Override
    public void unsubscribe(String platformName, String rateName) {
        try {
            if (!subscriptions.contains(rateName)) {
                System.out.println("⚠️ Not subscribed to " + rateName);
                return;
            }
            String command = "unsubscribe|" + rateName + "\n";
            writer.write(command.getBytes());
            writer.flush();
            subscriptions.remove(rateName);
            System.out.println("✅ Unsubscribed from " + rateName + " on " + platformName);
        } catch (Exception e) {
            System.err.println("❌ Failed to unsubscribe: " + e.getMessage());
        }
    }

    @Override
    public Rate fetchRate(String platformName, String rateName) {
        throw new UnsupportedOperationException("⚠️ TCP does not support fetching single rates.");
    }

    private void listenForData() {
        new Thread(() -> {
            while (isRunning) {
                try {
                    if (reader == null) {
                        System.out.println("❌ TCP veri okuyucu (reader) null, sunucu bağlantısı kesilmiş olabilir!");
                        break;
                    }

                    String response = reader.readLine();
                    if (response != null) {
                        System.out.println("📩 Received from TCP: " + response);

                        Rate parsedRate = parseRateFromTCP(response);
                        if (parsedRate != null && coordinator != null) {
                            // İlk kez gelen oran için
                            coordinator.onRateAvailable("TCP_PROVIDER", parsedRate.getRateName(), parsedRate);

                            // Sonraki güncellemeler için
                            coordinator.onRateUpdate("TCP_PROVIDER", parsedRate.getRateName(), parsedRate.getFields());
                        }
                    } else {
                        System.out.println("⚠️ TCP sunucusundan boş mesaj alındı, bağlantı düşmüş olabilir!");
                        break;
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error reading data: " + e.getMessage());
                    e.printStackTrace();
                    isRunning = false;
                }
            }
        }).start();
    }

    private Rate parseRateFromTCP(String response) {
        try {
            if (!response.contains("|")) {
                System.out.println("ℹ️ Info: TCP message is a control message, skipping: " + response);
                return null;
            }

            // Beklenen format:
            // PF1_USDTRY|22:number:34.736673|25:number:35.736673|5:timestamp:2025-02-10T11:50:04.932Z
            String[] parts = response.split("\\|");
            if (parts.length < 4) {
                System.err.println("❌ Invalid TCP message format, skipping: " + response);
                return null;
            }

            String rateName = parts[0];
            // örnek: "22:number:34.736673"
            String segment1 = parts[1];
            // örnek: "25:number:35.736673"
            String segment2 = parts[2];
            // örnek: "5:timestamp:2025-02-10T11:50:04.932Z"
            String segment3 = parts[3];

            double bid = parseNumberSegment(segment1);
            double ask = parseNumberSegment(segment2);
            long timestamp = parseTimestampSegment(segment3);

            // Rate oluştur
            RateFields fields = new RateFields(bid, ask, timestamp);
            RateStatus status = new RateStatus(true, true);
            return new Rate(rateName, fields, status);

        } catch (Exception e) {
            System.err.println("❌ Failed to parse TCP response: " + response);
            e.printStackTrace();
            return null;
        }
    }

    private double parseNumberSegment(String segment) {
        // "22:number:34.736673" şeklinde
        // split(":", 3) => [ "22", "number", "34.736673" ]
        String[] subParts = segment.split(":", 3);
        // subParts[0] = "22"    (ID)
        // subParts[1] = "number"
        // subParts[2] = "34.736673"
        return Double.parseDouble(subParts[2]);
    }

    private long parseTimestampSegment(String segment) {
        // "5:timestamp:2025-02-10T11:50:04.932Z"
        // split(":", 3) => [ "5", "timestamp", "2025-02-10T11:50:04.932Z" ]
        String[] subParts = segment.split(":", 3);
        // subParts[0] = "5"
        // subParts[1] = "timestamp"
        // subParts[2] = "2025-02-10T11:50:04.932Z"

        OffsetDateTime odt = OffsetDateTime.parse(subParts[2], CUSTOM_OFFSET_FORMATTER);
        return odt.toInstant().toEpochMilli();
    }


}
