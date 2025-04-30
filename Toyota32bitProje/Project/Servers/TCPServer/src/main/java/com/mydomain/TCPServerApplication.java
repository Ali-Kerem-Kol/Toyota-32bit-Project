package com.mydomain;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * TCPServerApplication sınıfı, TCP tabanlı bir sunucu başlatarak
 * istemcilerden abonelik komutları alır ve DataPublisher aracılığıyla
 * abone olunan döviz kurlarını istemcilere yayınlar.
 */
public class TCPServerApplication {
    private static final int PORT = 5000;
    private static final Set<String> subscribedPairs = Collections.synchronizedSet(new HashSet<>());
    private static final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
    private static final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private static volatile boolean serverRunning = true;

    /**
     * Uygulamanın giriş noktası. Belirtilen portta bir ServerSocket oluşturur,
     * istemcilerden bağlantıları kabul eder ve her bağlantı için yeni bir ClientHandler
     * thread'i başlatır. Aynı zamanda veri yayınlama iş parçacığını tetikler.
     *
     * @param args Program argümanları (kullanılmıyor)
     */
    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("TCP Server started. Port: " + PORT);

            // Veri yayın iş parçacığını başlat
            startDataPublisher();

            // Bağlantı kabul döngüsü
            while (serverRunning) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clients.add(clientHandler);
                executorService.submit(clientHandler);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * DataPublisher'ı belirli aralıklarla çalıştıran daemon thread'i başlatır.
     * Yayın frekansını ConfigReader üzerinden alır.
     */
    private static void startDataPublisher() {
        ConfigReader config = new ConfigReader("src/main/java/resources/config.json");
        new Thread(() -> {
            while (serverRunning) {
                DataPublisher.publishData(subscribedPairs, clients, config);
                try {
                    Thread.sleep(config.getPublishFrequency());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * Mevcut abone olunan döviz çiftlerini döner.
     *
     * @return Abone çiftlerini içeren Set
     */
    public static Set<String> getSubscribedPairs() {
        return subscribedPairs;
    }

    /**
     * ClientHandler sınıfı, her bir istemci bağlantısını ayrı bir thread üzerinde
     * yönetir. Gelen komutları işler ve istemciye yanıt döner.
     */
    static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private boolean quitRequested = false;

        /**
         * Yeni bir ClientHandler örneği oluşturur.
         *
         * @param socket İstemci ile iletişim kurmak için kullanılan Socket nesnesi
         */
        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        /**
         * Thread çalıştırıldığında komut girişlerini okur, "quit" komutu ile bağlantıyı sonlandırır
         * veya handleCommand metodunu çağırarak abone/abonelik iptal işlemlerini gerçekleştirir.
         */
        @Override
        public void run() {
            try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
            ) {
                this.out = out;
                out.println("Connection successful. Enter command:");

                String input;
                while ((input = in.readLine()) != null) {
                    System.out.println("Incoming message: " + input);

                    if ("quit".equalsIgnoreCase(input.trim())) {
                        out.println("Terminating connection...");
                        quitRequested = true;
                        break;
                    }

                    handleCommand(input, out);
                }
            } catch (IOException e) {
                System.out.println("Connection ended: " + socket.getInetAddress());
            } finally {
                try {
                    clients.remove(this);
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * İstemciden gelen komutu kontrol eder ve "subscribe" veya "unsubscribe" işlemlerini yapar.
         * Geçersiz istek formatı veya bulunmayan döviz çifti için hata mesajı döner.
         *
         * @param input Gelen komut satırı
         * @param out   PrintWriter ile istemciye yanıt gönderme nesnesi
         */
        private void handleCommand(String input, PrintWriter out) {
            if (input.startsWith("subscribe|")) {
                String pair = input.split("\\|")[1];
                if (!isValidCurrencyPair(pair)) {
                    out.println("ERROR|Rate data not found for " + pair);
                    return;
                }
                subscribedPairs.add(pair);
                out.println("Subscribed to " + pair);
            } else if (input.startsWith("unsubscribe|")) {
                String pair = input.split("\\|")[1];
                if (subscribedPairs.remove(pair)) {
                    out.println("Unsubscribed from " + pair);
                } else {
                    out.println("ERROR|Invalid subscription for " + pair);
                }
            } else {
                out.println("ERROR|Invalid request format");
            }
        }

        /**
         * Geçerli döviz çiftleri listesinde olup olmadığını kontrol eder.
         *
         * @param pair Kontrol edilecek döviz çifti
         * @return Eğer çift geçerliyse true, değilse false
         */
        private boolean isValidCurrencyPair(String pair) {
            ConfigReader config = new ConfigReader("src/main/java/resources/config.json");
            Set<String> validPairs = config.getInitialRates(); // Ensure this method exists in ConfigReader
            return validPairs.contains(pair);
        }

        /**
         * Bu istemciye veri gönderiminde kullanılan PrintWriter nesnesini döner.
         *
         * @return PrintWriter ile istemciye veri yazma nesnesi
         */
        public PrintWriter getWriter() {
            return out;
        }
    }
}
