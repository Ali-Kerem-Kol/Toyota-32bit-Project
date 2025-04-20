package com.mydomain;

import java.io.PrintWriter;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class DataPublisher {
    private static final Random random = new Random();

    public static void publishData(Set<String> subscribedPairs,
                                   List<TCPServerApplication.ClientHandler> clients,
                                   ConfigReader config) {
        if (subscribedPairs.isEmpty()) return;  // Eğer kimse abone değilse, yayın yapma

        double usdTryRate = config.getInitialRate("PF1_USDTRY");
        double eurUsdRate = config.getInitialRate("PF1_EURUSD");
        double gbpUsdRate = config.getInitialRate("PF1_GBPUSD");

        // 1) Şu andaki UTC zamanını al
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));

        // 2) Milis saniyeye (3 basamak) truncate et
        now = now.truncatedTo(ChronoUnit.MILLIS);

        // 3) ISO_OFFSET_DATE_TIME formatında stringe çevir
        String isoDateStr = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        for (TCPServerApplication.ClientHandler client : clients) {
            PrintWriter out = client.getWriter();
            if (out != null) {
                for (String pair : subscribedPairs) {
                    String message = "";
                    if (pair.equals("PF1_USDTRY")) {
                        // Bid
                        usdTryRate = CurrencySimulator.simulateExchangeRate(usdTryRate);
                        // Ask => bid + küçük bir spread
                        double ask = usdTryRate + 0.01;
                        message = String.format("PF1_USDTRY|22:number:%.6f|25:number:%.6f|5:timestamp:%s",
                                usdTryRate, ask, isoDateStr);

                    } else if (pair.equals("PF1_EURUSD")) {
                        eurUsdRate = CurrencySimulator.simulateExchangeRate(eurUsdRate);
                        double ask = eurUsdRate + 0.01;
                        message = String.format("PF1_EURUSD|22:number:%.6f|25:number:%.6f|5:timestamp:%s",
                                eurUsdRate, ask, isoDateStr);

                    } else if (pair.equals("PF1_GBPUSD")) {
                        gbpUsdRate = CurrencySimulator.simulateExchangeRate(gbpUsdRate);
                        double ask = gbpUsdRate + 0.01;
                        message = String.format("PF1_GBPUSD|22:number:%.6f|25:number:%.6f|5:timestamp:%s",
                                gbpUsdRate, ask, isoDateStr);
                    }

                    if (!message.isEmpty()) {
                        out.println(message);
                        out.flush();
                    }
                }
            }
        }
    }
}
