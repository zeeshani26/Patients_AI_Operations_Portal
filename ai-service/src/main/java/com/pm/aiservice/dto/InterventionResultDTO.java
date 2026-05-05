package com.pm.aiservice.dto;

import java.util.List;

public class InterventionResultDTO {
  private String intervention;
  private Double baselineRiskScore;
  private Double counterfactualRiskScore;
  private Double deltaRiskScore;
  private List<String> affectedFactors;

  public String getIntervention() {
    return intervention;
  }

  public void setIntervention(String intervention) {
    this.intervention = intervention;
  }

  public Double getBaselineRiskScore() {
    return baselineRiskScore;
  }

  public void setBaselineRiskScore(Double baselineRiskScore) {
    this.baselineRiskScore = baselineRiskScore;
  }

  public Double getCounterfactualRiskScore() {
    return counterfactualRiskScore;
  }

  public void setCounterfactualRiskScore(Double counterfactualRiskScore) {
    this.counterfactualRiskScore = counterfactualRiskScore;
  }

  public Double getDeltaRiskScore() {
    return deltaRiskScore;
  }

  public void setDeltaRiskScore(Double deltaRiskScore) {
    this.deltaRiskScore = deltaRiskScore;
  }

  public List<String> getAffectedFactors() {
    return affectedFactors;
  }

  public void setAffectedFactors(List<String> affectedFactors) {
    this.affectedFactors = affectedFactors;
  }
}

