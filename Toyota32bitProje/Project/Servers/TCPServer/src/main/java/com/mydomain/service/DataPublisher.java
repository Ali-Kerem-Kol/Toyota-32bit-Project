package com.mydomain.service;

import com.mydomain.config.ConfigReader;
import com.mydomain.simulation.CurrencySimulator;
import com.mydomain.server.ClientHandler;
import com.mydomain.server.ClientManager;

import java.io.PrintWriter;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;

public class DataPublisher {

    private final ConfigReader config;
    private final CurrencySimulator simulator;
    private final ClientManager clientManager;

    public DataPublisher(ConfigReader config, CurrencySimulator simulator, ClientManager clientManager) {
        this.config = config;
        this.simulator = simulator;
        this.clientManager = clientManager;
    }

    public void publishAllRates() {
        if (clientManager.getAllClients().isEmpty()) return;

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC")).truncatedTo(ChronoUnit.MILLIS);
        String isoDateStr = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        clientManager.getAllClients().forEach(client -> {
            PrintWriter out = client.getWriter();
            if (out == null) return;

            for (String rateName : client.getSubscribedRates()) {
                double initialRate = config.getInitialRate(rateName);
                double bid = simulator.simulateExchangeRate(rateName, initialRate, true); // sadece gönderilirse sayaç artar
                double ask = bid + 0.01;

                String message = String.format(
                        "%s|22:number:%.6f|25:number:%.6f|5:timestamp:%s",
                        rateName, bid, ask, isoDateStr
                );

                out.println(message);
                out.flush();
            }
        });
    }
}
