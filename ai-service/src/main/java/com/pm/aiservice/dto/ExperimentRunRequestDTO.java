package com.pm.aiservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class ExperimentRunRequestDTO {
  @NotBlank
  private String modelName;

  @NotBlank
  private String scenarioName;

  @Min(0)
  private double avgLatencyMs;

  @Min(0)
  @Max(100)
  private double availabilityPct;

  @Min(0)
  @Max(100)
  private double stabilityUnderFaultPct;

  @Min(0)
  @Max(10)
  private double explainabilityScore;

  @Min(0)
  @Max(100)
  private double fallbackCorrectnessPct;

  private String notes;

  public String getModelName() {
    return modelName;
  }

  public void setModelName(String modelName) {
    this.modelName = modelName;
  }

  public String getScenarioName() {
    return scenarioName;
  }

  public void setScenarioName(String scenarioName) {
    this.scenarioName = scenarioName;
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

  public double getStabilityUnderFaultPct() {
    return stabilityUnderFaultPct;
  }

  public void setStabilityUnderFaultPct(double stabilityUnderFaultPct) {
    this.stabilityUnderFaultPct = stabilityUnderFaultPct;
  }

  public double getExplainabilityScore() {
    return explainabilityScore;
  }

  public void setExplainabilityScore(double explainabilityScore) {
    this.explainabilityScore = explainabilityScore;
  }

  public double getFallbackCorrectnessPct() {
    return fallbackCorrectnessPct;
  }

  public void setFallbackCorrectnessPct(double fallbackCorrectnessPct) {
    this.fallbackCorrectnessPct = fallbackCorrectnessPct;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }
}
