package com.mydomain.consumer.consumer_postgresql.service;

import com.mydomain.consumer.consumer_postgresql.model.TblRates;
import com.mydomain.consumer.consumer_postgresql.repository.RatesRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

/**
 * TblRates nesnelerini veritabanına kaydeden servis sınıfı.
 */
@Service
@Log4j2
public class DatabaseService {

    private final RatesRepository ratesRepository;

    public DatabaseService(RatesRepository ratesRepository) {
        this.ratesRepository = ratesRepository;
    }

    /**
     * Verilen rate bilgisini veritabanına kaydeder.
     *
     * @param rate Kaydedilecek TblRates nesnesi
     */
    public void saveRate(TblRates rate) {
        try {
            log.debug("🗃 Saving to database → rateName={}, bid={}, ask={}, rateTime={}",
                    rate.getRateName(),
                    rate.getBid(),
                    rate.getAsk(),
                    rate.getRateUpdateTime());

            ratesRepository.save(rate);

            log.info("✅ Rate saved to DB → rateName={}, bid={}, ask={}",
                    rate.getRateName(),
                    rate.getBid(),
                    rate.getAsk());

        } catch (Exception e) {
            log.error("❌ Failed to save rate to DB → {}", e.getMessage(), e);
        }
    }
}