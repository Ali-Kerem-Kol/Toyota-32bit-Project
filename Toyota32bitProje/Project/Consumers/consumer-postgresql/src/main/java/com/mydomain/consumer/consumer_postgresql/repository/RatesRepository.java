package com.mydomain.consumer.consumer_postgresql.repository;

import com.mydomain.consumer.consumer_postgresql.model.TblRates;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository => CRUD metotlarını otomatik oluşturur.
 */
@Repository
public interface RatesRepository extends JpaRepository<TblRates, Long> {
}
