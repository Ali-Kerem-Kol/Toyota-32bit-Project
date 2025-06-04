package com.mydomain.server;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientManager {

    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    public void addClient(ClientHandler client) {
        clients.add(client);
    }

    public void removeClient(ClientHandler client) {
        clients.remove(client);
    }

    public List<ClientHandler> getClients() {
        return clients;
    }

    public void broadcast(String message, String rateName) {
        for (ClientHandler client : clients) {
            if (client.getSubscribedRates().contains(rateName)) {
                client.send(message);
            }
        }
    }

    public List<ClientHandler> getAllClients() {
        return clients;
    }
}
