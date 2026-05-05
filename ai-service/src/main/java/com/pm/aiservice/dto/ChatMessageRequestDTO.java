package com.pm.aiservice.dto;

import jakarta.validation.constraints.NotBlank;

public class ChatMessageRequestDTO {
  @NotBlank
  private String message;

  /**
   * Optional UI context; not {@code @Valid}-nested — chat must work with partial patient fields
   * (empty DOB, etc.) without failing the same rules as assess-risk.
   */
  private PatientRiskAssessmentRequest patientContext;

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public PatientRiskAssessmentRequest getPatientContext() {
    return patientContext;
  }

  public void setPatientContext(PatientRiskAssessmentRequest patientContext) {
    this.patientContext = patientContext;
  }
}
