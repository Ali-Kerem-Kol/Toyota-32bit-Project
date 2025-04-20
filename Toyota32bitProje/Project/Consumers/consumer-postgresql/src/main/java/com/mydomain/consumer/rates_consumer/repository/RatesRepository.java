package com.mydomain.consumer.rates_consumer.repository;

import com.mydomain.consumer.rates_consumer.model.TblRates;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository => CRUD metotlarını otomatik oluşturur.
 */
@Repository
public interface RatesRepository extends JpaRepository<TblRates, Long> {
}
