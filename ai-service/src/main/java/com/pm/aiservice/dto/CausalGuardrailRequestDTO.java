package com.pm.aiservice.dto;

public class CausalGuardrailRequestDTO {
  private double aiLatencyMs;
  private double dbLatencyMs;
  private double kafkaLag;
  private double errorRatePct;
  private double trafficLoadPct;

  public double getAiLatencyMs() {
    return aiLatencyMs;
  }

  public void setAiLatencyMs(double aiLatencyMs) {
    this.aiLatencyMs = aiLatencyMs;
  }

  public double getDbLatencyMs() {
    return dbLatencyMs;
  }

  public void setDbLatencyMs(double dbLatencyMs) {
    this.dbLatencyMs = dbLatencyMs;
  }

  public double getKafkaLag() {
    return kafkaLag;
  }

  public void setKafkaLag(double kafkaLag) {
    this.kafkaLag = kafkaLag;
  }

  public double getErrorRatePct() {
    return errorRatePct;
  }

  public void setErrorRatePct(double errorRatePct) {
    this.errorRatePct = errorRatePct;
  }

  public double getTrafficLoadPct() {
    return trafficLoadPct;
  }

  public void setTrafficLoadPct(double trafficLoadPct) {
    this.trafficLoadPct = trafficLoadPct;
  }
}
