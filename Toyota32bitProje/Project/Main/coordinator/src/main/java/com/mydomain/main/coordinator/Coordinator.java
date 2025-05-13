package com.mydomain.main.coordinator;

import com.mydomain.main.service.RateCalculatorService;
import com.mydomain.main.service.RedisService;
import com.mydomain.main.model.Rate;
import com.mydomain.main.model.RateFields;
import com.mydomain.main.model.RateStatus;
import com.mydomain.main.service.KafkaProducerService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

/**
 * Uygulamanın ana koordinatör sınıfı.
 * <p>
 * Veri sağlayıcılarından gelen olayları (connect, disconnect, yeni veri, güncelleme, durum)
 * karşılar; ham veriyi Redis'e kaydeder, hesaplama servisini çalıştırır ve
 * hesaplanan sonuçları Kafka'ya gönderir.
 * </p>
 */
public class Coordinator implements ICoordinator {

    private static final Logger logger = LogManager.getLogger(Coordinator.class);

    private final RedisService redisService;
    private final RateCalculatorService rateCalculatorService;
    private final KafkaProducerService kafkaProducerService;

    public Coordinator(RedisService redisService, RateCalculatorService rateCalculatorService,KafkaProducerService kafkaProducerService) {
        this.redisService = redisService;
        this.rateCalculatorService = rateCalculatorService;
        this.kafkaProducerService = kafkaProducerService;
    }


    /**
     * Sağlayıcı ile bağlantı kurulduğunda çağrılır.
     *
     * @param platformName Sağlayıcı adı (örneğin "TCP_PLATFORM")
     * @param status       Bağlantı durumu (true = bağlı, false = kopuk)
     */
    @Override
    public void onConnect(String platformName, Boolean status) {
        logger.info("🔗 {} connection status: {}", platformName, status ? "Connected" : "Disconnected");
    }

    /**
     * Sağlayıcı bağlantısı kesildiğinde çağrılır.
     *
     * @param platformName Sağlayıcı adı
     * @param status       Çıkış işleminin başarılı olup olmadığı
     */
    @Override
    public void onDisConnect(String platformName, Boolean status) {
        logger.info("🔴 {} disconnected.", platformName);
    }

    /**
     * Yeni bir oran verisi ilk defa geldiğinde çağrılır.
     * Ham veri Redis'e {@code raw:rateName} önekiyle kaydedilir ve
     * hesaplama servisi tetiklenir.
     *
     * @param platformName Sağlayıcı adı
     * @param rateName     Oran adı (örneğin "PF1_USDTRY")
     * @param rate         Gelen Rate nesnesi
     */
    @Override
    public void onRateAvailable(String platformName, String rateName, Rate rate) {
        redisService.putRawRate(rateName, rate);
        logger.info("📈 New Rate Available ({}): {}", platformName, rate);
        rateCalculatorService.calculate();
    }

    /**
     * Mevcut bir oran güncellendiğinde çağrılır.
     * Önce Redis'teki ham veri güncellenir, sonra hesaplama servisi
     * ve Kafka üretici servisi tetiklenir.
     *
     * @param platformName Sağlayıcı adı
     * @param rateName     Oran adı
     * @param rateFields   Yeni değerleri içeren RateFields nesnesi
     */
    @Override
    public void onRateUpdate(String platformName, String rateName, RateFields rateFields) {
        try {
            Rate rate = redisService.getRawRate(rateName);

            if (rate == null) {
                rate = new Rate(rateName, rateFields, new RateStatus(true, true));
                onRateAvailable(platformName, rateName, rate);
                return;           // onRateAvailable içinde zaten hesaplama tetikleniyor
            }

            rate.setFields(rateFields);
            redisService.putRawRate(rateName, rate);

            logger.info("📊 Rate Updated ({}): {} -> {}", platformName, rateName, rateFields);

            Map<String, Rate> results = rateCalculatorService.calculate();
            if (!results.isEmpty()) {
                kafkaProducerService.sendCalculatedRatesToKafka();
            }
        } catch (Exception e) {
            logger.error("❌ Error in onRateUpdate: {}", e.getMessage(), e);
        }
    }

    /**
     * Oran durumu (aktif/pasif) güncellendiğinde çağrılır.
     * Redis'teki ham veri üzerinde status alanı güncellenir.
     *
     * @param platformName Sağlayıcı adı
     * @param rateName     Oran adı
     * @param rateStatus   Yeni RateStatus nesnesi
     */
    @Override
    public void onRateStatus(String platformName, String rateName, RateStatus rateStatus) {
        Rate rate = redisService.getRawRate(rateName);
        if (rate != null) {
            rate.setStatus(rateStatus);
            redisService.putRawRate(rateName, rate);
        }
        logger.info("ℹ️ Rate Status Updated ({}): {} -> {}", platformName, rateName, rateStatus);
    }

}
