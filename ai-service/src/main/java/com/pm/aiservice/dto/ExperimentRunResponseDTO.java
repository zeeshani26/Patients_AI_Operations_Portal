package com.pm.aiservice.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public class ExperimentRunResponseDTO {
  private UUID id;
  private String modelName;
  private String scenarioName;
  private double avgLatencyMs;
  private double availabilityPct;
  private double stabilityUnderFaultPct;
  private double explainabilityScore;
  private double fallbackCorrectnessPct;
  private String notes;
  private LocalDateTime createdAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

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

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
