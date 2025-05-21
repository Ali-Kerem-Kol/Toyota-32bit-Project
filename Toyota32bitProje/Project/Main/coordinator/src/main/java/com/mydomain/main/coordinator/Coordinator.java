package com.mydomain.main.coordinator;

import com.mydomain.main.model.Rate;
import com.mydomain.main.model.RateFields;
import com.mydomain.main.model.RateStatus;
import com.mydomain.main.service.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

public class Coordinator implements ICoordinator {

    private static final Logger logger = LogManager.getLogger(Coordinator.class);

    private final RedisProducerService rawProducerService;
    private final RedisConsumerService rawConsumerService;
    private final RedisProducerService calculatedProducerService;
    private final RedisConsumerService calculatedConsumerService;

    private final RateCalculatorService rateCalculatorService;
    private final KafkaProducerService kafkaProducerService;

    public Coordinator(
            RedisProducerService rawProducerService,
            RedisConsumerService rawConsumerService,
            RedisProducerService calculatedProducerService,
            RedisConsumerService calculatedConsumerService,
            RateCalculatorService rateCalculatorService,
            KafkaProducerService kafkaProducerService
    ) {
        this.rawProducerService = rawProducerService;
        this.rawConsumerService = rawConsumerService;
        this.calculatedProducerService = calculatedProducerService;
        this.calculatedConsumerService = calculatedConsumerService;
        this.rateCalculatorService = rateCalculatorService;
        this.kafkaProducerService = kafkaProducerService;
    }

    @Override
    public void onRateAvailable(String platformName, String rateName, Rate rate) {
        rawProducerService.publishRate(rateName, rate);
        logger.info("📈 New Rate Available ({}): {}", platformName, rate);
    }

    @Override
    public void onRateUpdate(String platformName, String rateName, RateFields rateFields) {
        Rate updatedRate = new Rate(rateName, rateFields, new RateStatus(true, true));
        rawProducerService.publishRate(rateName, updatedRate);
        logger.info("📊 Rate Updated ({}): {} -> {}", platformName, rateName, rateFields);
    }

    @Override
    public void onConnect(String platformName, Boolean status) {
        logger.info("🔗 {} connection status: {}", platformName, status ? "Connected" : "Disconnected");
    }

    @Override
    public void onDisConnect(String platformName, Boolean status) {
        logger.info("🔴 {} disconnected", platformName);
    }

    @Override
    public void onRateStatus(String platformName, String rateName, RateStatus rateStatus) {
        logger.info("ℹ️ Rate Status Updated ({}): {} -> {}", platformName, rateName, rateStatus);
    }

    /**
     * Stream'den veri okuyarak hesaplama ve Kafka gönderim döngüsünü başlatır.
     * Uygulama açıldığında bir kez çağrılmalıdır.
     */
    public void startStreamConsumerLoop() {
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // 1. raw_rates stream'den verileri oku ve grupla
                    Map<String, List<Rate>> groupedRawRates = rawConsumerService.readAndGroupRawRates();
                    if (groupedRawRates.isEmpty()) continue;

                    // 2. Hesapla
                    Map<String, Rate> calculatedRates = rateCalculatorService.calculate(groupedRawRates);
                    if (calculatedRates.isEmpty()) continue;

                    // 3. calculated stream’e yaz
                    for (Map.Entry<String, Rate> entry : calculatedRates.entrySet()) {
                        calculatedProducerService.publishRate(entry.getKey(), entry.getValue());
                    }

                    // 4. calculated stream’den oku ve Kafka’ya gönder
                    Map<String, Rate> ratesToSend = calculatedConsumerService.readAndGroupCalculatedRates();
                    if (ratesToSend.isEmpty()) continue;

                    kafkaProducerService.sendCalculatedRatesToKafka(ratesToSend);

                } catch (Exception e) {
                    logger.error("❌ Error in stream consumer loop: {}", e.getMessage(), e);
                }
            }
        }, "stream-reader-thread");

        t.setDaemon(true);
        t.start();
    }

}
