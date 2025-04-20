package com.mydomain.main.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * <p>Rate represents a single rate entity with rateName,
 * RateFields (bid, ask, timestamp), and RateStatus (active, updated).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Rate {
    private String rateName;
    private RateFields fields;
    private RateStatus status;

    public Rate() {
    }

    /**
     * Constructor for JSON deserialization.
     */
    public Rate(@JsonProperty("rateName") String rateName,
                @JsonProperty("bid") double bid,
                @JsonProperty("ask") double ask,
                @JsonProperty("timestamp") String timestamp) {
        this.rateName = rateName;
        this.fields = new RateFields(bid, ask, timestamp);
        this.status = new RateStatus(true, true);
    }

    public Rate(String rateName, RateFields fields, RateStatus status) {
        this.rateName = rateName;
        this.fields = fields;
        this.status = status;
    }

    public String getRateName() {
        return rateName;
    }

    public void setRateName(String rateName) {
        this.rateName = rateName;
    }

    public RateFields getFields() {
        return fields;
    }

    public void setFields(RateFields fields) {
        this.fields = fields;
    }

    public RateStatus getStatus() {
        return status;
    }

    public void setStatus(RateStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "Rate{" +
                "rateName='" + rateName + '\'' +
                ", fields=" + fields +
                ", status=" + status +
                '}';
    }

}
