package com.pm.aiservice.dto;

public class ModelComparisonDTO {
  private String modelName;
  private double avgLatencyMs;
  private double availabilityPct;
  private double explainabilityScore;
  private double stabilityUnderFaultPct;

  public String getModelName() {
    return modelName;
  }

  public void setModelName(String modelName) {
    this.modelName = modelName;
  }

  public double getAvgLatencyMs() {
    return avgLatencyMs;
  }

  public void setAvgLatencyMs(double avgLatencyMs) {
    this.avgLatencyMs = avgLatencyMs;
  }

  public double getAvailabilityPct() {
    return availabilityPct;
  }

  public void setAvailabilityPct(double availabilityPct) {
    this.availabilityPct = availabilityPct;
  }

  public double getExplainabilityScore() {
    return explainabilityScore;
  }

  public void setExplainabilityScore(double explainabilityScore) {
    this.explainabilityScore = explainabilityScore;
  }

  public double getStabilityUnderFaultPct() {
    return stabilityUnderFaultPct;
  }

  public void setStabilityUnderFaultPct(double stabilityUnderFaultPct) {
    this.stabilityUnderFaultPct = stabilityUnderFaultPct;
  }
}
