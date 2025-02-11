package com.mydomain.main.model;

public class RateStatus {
    private boolean isActive; // Oran aktif mi?
    private boolean isUpdated; // Son alınan veri güncel mi?

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
