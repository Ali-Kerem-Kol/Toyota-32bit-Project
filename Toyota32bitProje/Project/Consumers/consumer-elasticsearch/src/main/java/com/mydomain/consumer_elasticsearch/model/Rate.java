package com.mydomain.consumer_elasticsearch.model;

public class Rate {
    private String rateName;
    private RateFields fields;
    private RateStatus status;

    public Rate() {}

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
