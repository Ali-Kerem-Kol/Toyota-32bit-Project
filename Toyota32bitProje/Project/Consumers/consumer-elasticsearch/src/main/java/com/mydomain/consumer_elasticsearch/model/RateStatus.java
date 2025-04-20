package com.mydomain.consumer_elasticsearch.model;

public class RateStatus {
    private boolean isActive;
    private boolean isUpdated;

    public RateStatus() {}

    public RateStatus(boolean isActive, boolean isUpdated) {
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
