package com.pm.aiservice.dto;

import java.util.List;

public class InterventionAnalysisResponseDTO {
  private String patientId;
  private String interventionType;
  private String interventionValue;
  private double baselineRiskScore;
  private double counterfactualRiskScore;
  private double deltaRiskScore;
  private double relativeRiskReductionPct;
  private String recommendation;
  private List<String> assumptions;
  private double confidenceScore;

  public String getPatientId() {
    return patientId;
  }

  public void setPatientId(String patientId) {
    this.patientId = patientId;
  }

  public String getInterventionType() {
    return interventionType;
  }

  public void setInterventionType(String interventionType) {
    this.interventionType = interventionType;
  }

  public String getInterventionValue() {
    return interventionValue;
  }

  public void setInterventionValue(String interventionValue) {
    this.interventionValue = interventionValue;
  }

  public double getBaselineRiskScore() {
    return baselineRiskScore;
  }

  public void setBaselineRiskScore(double baselineRiskScore) {
    this.baselineRiskScore = baselineRiskScore;
  }

  public double getCounterfactualRiskScore() {
    return counterfactualRiskScore;
  }

  public void setCounterfactualRiskScore(double counterfactualRiskScore) {
    this.counterfactualRiskScore = counterfactualRiskScore;
  }

  public double getDeltaRiskScore() {
    return deltaRiskScore;
  }

  public void setDeltaRiskScore(double deltaRiskScore) {
    this.deltaRiskScore = deltaRiskScore;
  }

  public double getRelativeRiskReductionPct() {
    return relativeRiskReductionPct;
  }

  public void setRelativeRiskReductionPct(double relativeRiskReductionPct) {
    this.relativeRiskReductionPct = relativeRiskReductionPct;
  }

  public String getRecommendation() {
    return recommendation;
  }

  public void setRecommendation(String recommendation) {
    this.recommendation = recommendation;
  }

  public List<String> getAssumptions() {
    return assumptions;
  }

  public void setAssumptions(List<String> assumptions) {
    this.assumptions = assumptions;
  }

  public double getConfidenceScore() {
    return confidenceScore;
  }

  public void setConfidenceScore(double confidenceScore) {
    this.confidenceScore = confidenceScore;
  }
}
