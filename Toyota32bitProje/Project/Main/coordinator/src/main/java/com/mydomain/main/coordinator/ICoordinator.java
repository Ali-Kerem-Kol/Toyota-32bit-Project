package com.mydomain.main.coordinator;

import com.mydomain.main.model.Rate;
import com.mydomain.main.model.RateFields;
import com.mydomain.main.model.RateStatus;

/**
 * <p>CoordinatorInterface defines callback methods for connection events,
 * new data arrival, updates, and status changes of rates. Also a stub for
 * fetching rates from REST on demand.</p>
 */
public interface ICoordinator {

    /**
     * Called when a provider (TCP or REST) establishes a connection.
     * @param platformName Name of the platform (e.g. "TCP_PLATFORM")
     * @param status True if connected, false if not
     */
    void onConnect(String platformName, Boolean status);

    /**
     * Called when a provider is disconnected.
     * @param platformName platform name
     * @param status True if successful
     */
    void onDisConnect(String platformName, Boolean status);

    /**
     * Called when a new rate is first available.
     * @param platformName platform name
     * @param rateName the rate key
     * @param rate the Rate object
     */
    void onRateAvailable(String platformName, String rateName, Rate rate);

    /**
     * Called on subsequent updates to an existing rate.
     * @param platformName platform name
     * @param rateName the rate key
     * @param rateFields new field values (bid, ask, etc.)
     */
    void onRateUpdate(String platformName, String rateName, RateFields rateFields);

    /**
     * Called on status changes for a rate (active/inactive).
     * @param platformName platform name
     * @param rateName the rate key
     * @param rateStatus updated status
     */
    void onRateStatus(String platformName, String rateName, RateStatus rateStatus);

    /**
     * Optional method to fetch a rate from REST provider on demand.
     * @param platformName platform name
     * @param rateName the rate key
     * @return Rate if found, or null
     */
    Rate fetchRateFromRest(String platformName, String rateName);
}
