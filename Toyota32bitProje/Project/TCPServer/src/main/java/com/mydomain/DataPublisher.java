package com.mydomain;

import java.io.PrintWriter;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class DataPublisher {
    private static final Random random = new Random();

    public static void publishData(Set<String> subscribedPairs, List<TCPServer.ClientHandler> clients, ConfigReader config) {
        if (subscribedPairs.isEmpty()) return;  // Eğer kimse abone değilse, yayın yapma

        double usdTryRate = config.getInitialRate("PF1_USDTRY");
        double eurUsdRate = config.getInitialRate("PF1_EURUSD");

        for (TCPServer.ClientHandler client : clients) {
            PrintWriter out = client.getWriter();
            if (out != null) {
                for (String pair : subscribedPairs) {
                    String message = "";
                    if (pair.equals("PF1_USDTRY")) {
                        usdTryRate = CurrencySimulator.simulateExchangeRate(usdTryRate);
                        message = String.format("PF1_USDTRY|22:number:%.6f|25:number:%.6f|5:timestamp:%s",
                                usdTryRate, usdTryRate + 1, new Date());
                    } else if (pair.equals("PF1_EURUSD")) {
                        eurUsdRate = CurrencySimulator.simulateExchangeRate(eurUsdRate);
                        message = String.format("PF1_EURUSD|22:number:%.6f|25:number:%.6f|5:timestamp:%s",
                                eurUsdRate, eurUsdRate + 1, new Date());
                    }

                    if (!message.isEmpty()) {
                        out.println(message);
                        out.flush();  // Telnet istemcisine veriyi hemen gönder
                    }
                }
            }
        }
    }
}
