package com.mydomain;

import com.mydomain.config.ConfigReader;
import com.mydomain.server.ClientHandler;
import com.mydomain.server.ClientManager;
import com.mydomain.service.DataPublisher;
import com.mydomain.simulation.CurrencySimulator;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TCPServerApplication {

    private static final int PORT = 5000;

    public static void main(String[] args) {
        ConfigReader config = new ConfigReader();
        ClientManager clientManager = new ClientManager();
        ExecutorService executor = Executors.newCachedThreadPool();
        CurrencySimulator simulator = new CurrencySimulator();
        DataPublisher dataPublisher = new DataPublisher(config, simulator, clientManager);

        // 1️⃣ Veri yayınlayıcı thread'i başlat
        Thread publisherThread = new Thread(() -> {
            while (true) {
                try {
                    dataPublisher.publishAllRates();
                    Thread.sleep(config.getPublishFrequency());
                } catch (InterruptedException e) {
                    System.out.println("🛑 Publisher thread interrupted.");
                    break;
                }
            }
        }, "publisher-thread");

        publisherThread.setDaemon(true);
        publisherThread.start();

        // 2️⃣ Server başlat
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("✅ TCP Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("🔌 New client connected: " + clientSocket.getInetAddress());

                ClientHandler handler = new ClientHandler(clientSocket, clientManager);
                clientManager.addClient(handler);
                executor.submit(handler);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            executor.shutdownNow();
            System.out.println("❌ Server shutting down.");
        }
    }
}
