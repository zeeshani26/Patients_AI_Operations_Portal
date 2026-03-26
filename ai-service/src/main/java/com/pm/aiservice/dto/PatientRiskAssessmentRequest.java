package com.pm.aiservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public class PatientRiskAssessmentRequest {

  @NotBlank(message = "Patient ID is required")
  private String patientId;

  @NotBlank(message = "Name is required")
  private String name;

  @NotBlank(message = "Email is required")
  @Email(message = "Invalid email format")
  private String email;

  @NotNull(message = "Date of birth is required")
  private LocalDate dateOfBirth;

  private String address;
  private Integer age;
  private String medicalHistory;
  private String currentMedications;
  private String allergies;

  // Getters and Setters
  public String getPatientId() {
    return patientId;
  }

  public void setPatientId(String patientId) {
    this.patientId = patientId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public LocalDate getDateOfBirth() {
    return dateOfBirth;
  }

  public void setDateOfBirth(LocalDate dateOfBirth) {
    this.dateOfBirth = dateOfBirth;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public Integer getAge() {
    return age;
  }

  public void setAge(Integer age) {
    this.age = age;
  }

  public String getMedicalHistory() {
    return medicalHistory;
  }

  public void setMedicalHistory(String medicalHistory) {
    this.medicalHistory = medicalHistory;
  }

  public String getCurrentMedications() {
    return currentMedications;
  }

  public void setCurrentMedications(String currentMedications) {
    this.currentMedications = currentMedications;
  }

  public String getAllergies() {
    return allergies;
  }

  public void setAllergies(String allergies) {
    this.allergies = allergies;
  }
}


