package com.mydomain;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class TCPServerApplication {
    private static final int PORT = 5000;
    private static final Set<String> subscribedPairs = Collections.synchronizedSet(new HashSet<>());
    private static final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
    private static final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private static volatile boolean serverRunning = true;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("TCP Server started. Port: " + PORT);

            // Veri yayın thread'ini başlat
            startDataPublisher();

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

    public static Set<String> getSubscribedPairs() {
        return subscribedPairs;
    }

    static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private boolean quitRequested = false;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

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

        private boolean isValidCurrencyPair(String pair) {
            return pair.equals("PF1_USDTRY") || pair.equals("PF1_EURUSD") || pair.equals("PF1_GBPUSD");
        }

        public PrintWriter getWriter() {
            return out;
        }
    }
}
