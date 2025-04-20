package com.mydomain.main.provider;

import com.mydomain.main.coordinator.ICoordinator;

import java.util.Map;

/**
 * <p>Provider interface defines the minimum required methods:
 * connect, disconnect, subscribe, unsubscribe for a data provider
 * (TCPProvider, RESTProvider, etc.).</p>
 */
public interface IProvider {

    /**
     * Alternative connect method using a map of params (e.g. host, port, apiKey).
     */
    void connect(String platformName, Map<String, String> params);

    /**
     * Disconnect from the platform.
     */
    void disConnect(String platformName, Map<String, String> params);

    /**
     * Subscribe to a specific rate.
     */
    void subscribe(String platformName, String rateName);

    /**
     * Unsubscribe from a specific rate.
     */
    void unSubscribe(String platformName, String rateName);

    /**
     * Coordinator reference assignment.
     */
    void setCoordinator(ICoordinator coordinator);

}
