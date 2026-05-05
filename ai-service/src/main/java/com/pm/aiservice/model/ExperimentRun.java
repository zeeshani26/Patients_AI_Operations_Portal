package com.pm.aiservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "experiment_runs", indexes = {
    @Index(name = "idx_experiment_model_name", columnList = "modelName"),
    @Index(name = "idx_experiment_created_at", columnList = "createdAt")
})
public class ExperimentRun {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String modelName;

  @Column(nullable = false)
  private String scenarioName;

  @Column(nullable = false)
  private double avgLatencyMs;

  @Column(nullable = false)
  private double availabilityPct;

  @Column(nullable = false)
  private double stabilityUnderFaultPct;

  @Column(nullable = false)
  private double explainabilityScore;

  @Column(nullable = false)
  private double fallbackCorrectnessPct;

  @Column(columnDefinition = "TEXT")
  private String notes;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
  }

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
