package com.mydomain.main.cache;

import com.mydomain.main.filter.FilterService;
import com.mydomain.main.model.Rate;
import com.mydomain.main.model.RateFields;
import com.mydomain.main.model.RateStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RateCache {

    private static final Logger logger = LogManager.getLogger(RateCache.class);


    private final int maxSize;
    private final Map<String, Map<String, ArrayDeque<Rate>>> cache;
    private final FilterService filterService;

    public RateCache(int maxSize, FilterService filterService) {
        this.maxSize = maxSize;
        this.cache = new ConcurrentHashMap<>();
        this.filterService = filterService;
    }


    public boolean isFirstRate(String platform, String rateName) {
        Map<String, ArrayDeque<Rate>> platformRates = cache.get(platform);
        if (platformRates == null) return true;
        ArrayDeque<Rate> deque = platformRates.get(rateName);
        return deque == null || deque.isEmpty();
    }

    public Rate addFirstRate(String platform, String rateName, RateFields rateFields) {
        Map<String, ArrayDeque<Rate>> platformRates =
                cache.computeIfAbsent(platform, p -> new ConcurrentHashMap<>());
        ArrayDeque<Rate> deque =
                platformRates.computeIfAbsent(rateName, r -> new ArrayDeque<>());


        synchronized (deque) {
            if (!deque.isEmpty()) return null; // If rate already exists, do not add
            Rate rate = new Rate(rateName, rateFields, new RateStatus(true, false));
            deque.addLast(rate);
            logger.trace("New rate added to cache: platform={}, rateName={}, rate={}", platform, rateName, rate);
            return rate;
        }
    }


    public Rate addNewRate(String platform, String rateName, RateFields updatedFields) {
        Map<String, ArrayDeque<Rate>> platformRates =
                cache.computeIfAbsent(platform, p -> new ConcurrentHashMap<>());
        ArrayDeque<Rate> deque =
                platformRates.computeIfAbsent(rateName, r -> new ArrayDeque<>());

        synchronized (deque) {
            if (deque.isEmpty()) return null; // If no existing rates, cannot update

            Rate last = deque.peekLast();
            Rate updated = new Rate(last);
            updated.setFields(updatedFields);
            updated.getStatus().setActive(true);
            updated.getStatus().setUpdated(true);

            List<Rate> history = new ArrayList<>(deque);
            if (!filterService.applyAllFilters(platform, rateName, last, updated, history)) return null;

            if (deque.size() >= maxSize) deque.pollFirst(); // Remove oldest rate if cache is full

            deque.addLast(updated);
            logger.trace("Rate updated in cache: platform={}, rateName={}, updated={}", platform, rateName, updated);
            return updated;
        }
    }



    public List<Rate> getRates(String platform, String rateName) {
        Map<String, ArrayDeque<Rate>> platformRates = cache.get(platform);
        if (platformRates == null) return Collections.emptyList();
        ArrayDeque<Rate> deque = platformRates.get(rateName);
        if (deque == null) return Collections.emptyList();
        synchronized (deque) {
            return new ArrayList<>(deque);
        }
    }

    public List<Rate> getActiveRates(String platform, String rateName) {
        List<Rate> active = new ArrayList<>();
        Map<String, ArrayDeque<Rate>> platformRates = cache.get(platform);
        if (platformRates == null) return active;
        ArrayDeque<Rate> deque = platformRates.get(rateName);
        if (deque == null) return active;
        synchronized (deque) {
            for (Rate r : deque) {
                if (r.getStatus().isActive()) {
                    active.add(r);
                }
            }
        }
        return active;
    }


    public void markRatesToNonActive(String platform, String rateName, List<Rate> ratesToMark) {
        Map<String, ArrayDeque<Rate>> platformRates = cache.get(platform);
        if (platformRates == null) return;
        ArrayDeque<Rate> deque = platformRates.get(rateName);
        if (deque == null) return;
        synchronized (deque) {
            for (Rate r : deque) {
                if (ratesToMark.contains(r)) {
                    r.getStatus().setActive(false);
                }
            }
        }
    }


    public void markRateToNonActive(String platform, String rateName, Rate rateToMark) {
        Map<String, ArrayDeque<Rate>> platformRates = cache.get(platform);
        if (platformRates == null) return;
        ArrayDeque<Rate> deque = platformRates.get(rateName);
        if (deque == null) return;
        synchronized (deque) {
            for (Rate r : deque) {
                if (r.equals(rateToMark)) {
                    r.getStatus().setActive(false);
                    logger.trace("Rate marked as inactive: platform={}, rateName={}, rate={}", platform, rateName, r);
                    break;
                }
            }
        }
    }
}
