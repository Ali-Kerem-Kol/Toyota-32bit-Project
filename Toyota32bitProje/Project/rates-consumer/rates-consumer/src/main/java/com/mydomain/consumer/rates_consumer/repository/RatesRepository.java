package com.mydomain.consumer.rates_consumer.repository;

import com.mydomain.consumer.rates_consumer.model.TblRates;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RatesRepository extends JpaRepository<TblRates, Long> {
}
