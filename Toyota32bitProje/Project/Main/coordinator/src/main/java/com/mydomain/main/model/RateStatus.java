package com.mydomain.main.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * <p>RateStatus indicates if a rate is active,
 * and whether it's been recently updated.</p>
 */
public class RateStatus {
    private boolean isActive;
    private boolean isUpdated;

    // Default constructor for Jackson deserialization
    public RateStatus() {
        // Varsayılan değerler atanabilir:
        // this.isActive = false;
        // this.isUpdated = false;
    }

    @JsonCreator
    public RateStatus(
            @JsonProperty("isActive") boolean isActive,
            @JsonProperty("isUpdated") boolean isUpdated) {
        this.isActive = isActive;
        this.isUpdated = isUpdated;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public boolean isUpdated() {
        return isUpdated;
    }

    public void setUpdated(boolean updated) {
        isUpdated = updated;
    }

    @Override
    public String toString() {
        return "RateStatus{" +
                "isActive=" + isActive +
                ", isUpdated=" + isUpdated +
                '}';
    }
}
