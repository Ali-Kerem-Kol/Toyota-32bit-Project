package com.mydomain.server;

import java.io.*;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final ClientManager clientManager;
    private PrintWriter out;
    private final Set<String> subscribedRates = new HashSet<>();

    public ClientHandler(Socket socket, ClientManager clientManager) {
        this.socket = socket;
        this.clientManager = clientManager;
    }

    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            this.out = out;
            out.println("Connected. Enter commands (subscribe|X, unsubscribe|X, quit)");

            String input;
            while ((input = in.readLine()) != null) {
                if (input.equalsIgnoreCase("quit")) break;
                handleCommand(input);
            }

        } catch (IOException e) {
            System.out.println("Connection closed: " + socket.getInetAddress());
        } finally {
            clientManager.removeClient(this);
            close();
        }
    }

    private void handleCommand(String input) {
        if (input.startsWith("subscribe|")) {
            String rate = input.split("\\|")[1];
            subscribedRates.add(rate);
            out.println("Subscribed to " + rate);
        } else if (input.startsWith("unsubscribe|")) {
            String rate = input.split("\\|")[1];
            if (subscribedRates.remove(rate)) {
                out.println("Unsubscribed from " + rate);
            } else {
                out.println("ERROR: Not subscribed to " + rate);
            }
        } else {
            out.println("ERROR: Unknown command");
        }
    }

    public void send(String message) {
        if (out != null) {
            out.println(message);
            out.flush();
        }
    }

    public Set<String> getSubscribedRates() {
        return subscribedRates;
    }

    public PrintWriter getWriter() {
        return out;
    }

    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
