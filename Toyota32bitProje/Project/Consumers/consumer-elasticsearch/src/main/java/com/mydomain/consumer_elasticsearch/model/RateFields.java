package com.mydomain.consumer_elasticsearch.model;

public class RateFields {
    private double bid;
    private double ask;
    private String timestamp;

    public RateFields() {}

    public RateFields(double bid, double ask, String timestamp) {
        this.bid = bid;
        this.ask = ask;
        this.timestamp = timestamp;
    }

    public double getBid() {
        return bid;
    }

    public void setBid(double bid) {
        this.bid = bid;
    }

    public double getAsk() {
        return ask;
    }

    public void setAsk(double ask) {
        this.ask = ask;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "RateFields{" +
                "bid=" + bid +
                ", ask=" + ask +
                ", timestamp='" + timestamp + '\'' +
                '}';
    }
}
