package com.pm.aiservice.dto;

public class CausalGuardrailResponseDTO {
  private String suspectedCause;
  private String selectedStrategy;
  private String selectedModelMode;
  private String rationale;
  private double confidenceScore;

  public String getSuspectedCause() {
    return suspectedCause;
  }

  public void setSuspectedCause(String suspectedCause) {
    this.suspectedCause = suspectedCause;
  }

  public String getSelectedStrategy() {
    return selectedStrategy;
  }

  public void setSelectedStrategy(String selectedStrategy) {
    this.selectedStrategy = selectedStrategy;
  }

  public String getSelectedModelMode() {
    return selectedModelMode;
  }

  public void setSelectedModelMode(String selectedModelMode) {
    this.selectedModelMode = selectedModelMode;
  }

  public String getRationale() {
    return rationale;
  }

  public void setRationale(String rationale) {
    this.rationale = rationale;
  }

  public double getConfidenceScore() {
    return confidenceScore;
  }

  public void setConfidenceScore(double confidenceScore) {
    this.confidenceScore = confidenceScore;
  }
}
