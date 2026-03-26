package com.pm.aiservice.dto;

import java.time.LocalDateTime;
import java.util.List;

public class PatientRiskAssessmentResponse {

  private String patientId;
  private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
  private Double riskScore; // 0.0 to 1.0
  private String assessment;
  private List<String> riskFactors;
  private List<String> recommendations;
  private Double confidenceScore;
  private LocalDateTime assessedAt;

  // Getters and Setters
  public String getPatientId() {
    return patientId;
  }

  public void setPatientId(String patientId) {
    this.patientId = patientId;
  }

  public String getRiskLevel() {
    return riskLevel;
  }

  public void setRiskLevel(String riskLevel) {
    this.riskLevel = riskLevel;
  }

  public Double getRiskScore() {
    return riskScore;
  }

  public void setRiskScore(Double riskScore) {
    this.riskScore = riskScore;
  }

  public String getAssessment() {
    return assessment;
  }

  public void setAssessment(String assessment) {
    this.assessment = assessment;
  }

  public List<String> getRiskFactors() {
    return riskFactors;
  }

  public void setRiskFactors(List<String> riskFactors) {
    this.riskFactors = riskFactors;
  }

  public List<String> getRecommendations() {
    return recommendations;
  }

  public void setRecommendations(List<String> recommendations) {
    this.recommendations = recommendations;
  }

  public Double getConfidenceScore() {
    return confidenceScore;
  }

  public void setConfidenceScore(Double confidenceScore) {
    this.confidenceScore = confidenceScore;
  }

  public LocalDateTime getAssessedAt() {
    return assessedAt;
  }

  public void setAssessedAt(LocalDateTime assessedAt) {
    this.assessedAt = assessedAt;
  }
}


