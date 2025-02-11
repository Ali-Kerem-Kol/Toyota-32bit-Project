package com.mydomain.main.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RateFields {
    private double bid;
    private double ask;
    private String timestamp; // JSON'dan string formatında geldiği için String olarak tutuyoruz

    public RateFields(@JsonProperty("bid") double bid,
                      @JsonProperty("ask") double ask,
                      @JsonProperty("timestamp") String timestamp) {
        this.bid = bid;
        this.ask = ask;
        this.timestamp = timestamp;
    }

    public RateFields(double bid, double ask, long timestamp) {
        this.bid = bid;
        this.ask = ask;
        this.timestamp = String.valueOf(timestamp); // long değeri stringe çeviriyoruz
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
