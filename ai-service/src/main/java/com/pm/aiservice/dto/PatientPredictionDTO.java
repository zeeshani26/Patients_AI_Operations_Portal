package com.pm.aiservice.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public class PatientPredictionDTO {
  private UUID id;
  private String patientId;
  private String predictionType;
  private String predictionResult;
  private Double confidenceScore;
  private String modelVersion;
  private LocalDateTime createdAt;

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

