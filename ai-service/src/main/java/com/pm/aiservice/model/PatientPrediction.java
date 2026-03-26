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
@Table(name = "patient_predictions", indexes = {
    @Index(name = "idx_patient_id", columnList = "patientId"),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
public class PatientPrediction {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String patientId;

  @Column(nullable = false)
  private String predictionType; // RISK_ASSESSMENT, ANOMALY_DETECTION, RECOMMENDATION

  @Column(nullable = false, columnDefinition = "TEXT")
  private String predictionResult;

  @Column(nullable = false)
  private Double confidenceScore; // 0.0 to 1.0

  @Column(nullable = false)
  private String modelVersion;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
  }

  // Getters and Setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getPatientId() {
    return patientId;
  }

  public void setPatientId(String patientId) {
    this.patientId = patientId;
  }

  public String getPredictionType() {
    return predictionType;
  }

  public void setPredictionType(String predictionType) {
    this.predictionType = predictionType;
  }

  public String getPredictionResult() {
    return predictionResult;
  }

  public void setPredictionResult(String predictionResult) {
    this.predictionResult = predictionResult;
  }

  public Double getConfidenceScore() {
    return confidenceScore;
  }

  public void setConfidenceScore(Double confidenceScore) {
    this.confidenceScore = confidenceScore;
  }

  public String getModelVersion() {
    return modelVersion;
  }

  public void setModelVersion(String modelVersion) {
    this.modelVersion = modelVersion;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}


