package com.pm.aiservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class InterventionAnalysisRequestDTO {
  @NotNull
  @Valid
  private PatientRiskAssessmentRequest patient;

  @NotBlank
  private String interventionType;

  private String interventionValue;

  public PatientRiskAssessmentRequest getPatient() {
    return patient;
  }

  public void setPatient(PatientRiskAssessmentRequest patient) {
    this.patient = patient;
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
}
