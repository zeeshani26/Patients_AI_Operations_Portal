package com.pm.analyticsservice.model;

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
@Table(name = "patient_event_analytics", indexes = {
    @Index(name = "idx_event_type", columnList = "eventType"),
    @Index(name = "idx_created_at", columnList = "createdAt"),
    @Index(name = "idx_patient_id", columnList = "patientId")
})
public class PatientEventAnalytics {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String patientId;

  @Column(nullable = false)
  private String patientName;

  @Column(nullable = false)
  private String patientEmail;

  @Column(nullable = false)
  private String eventType; // PATIENT_CREATED, PATIENT_UPDATED, PATIENT_DELETED

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private LocalDateTime processedAt;

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    processedAt = LocalDateTime.now();
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

  public String getPatientName() {
    return patientName;
  }

  public void setPatientName(String patientName) {
    this.patientName = patientName;
  }

  public String getPatientEmail() {
    return patientEmail;
  }

  public void setPatientEmail(String patientEmail) {
    this.patientEmail = patientEmail;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getProcessedAt() {
    return processedAt;
  }

  public void setProcessedAt(LocalDateTime processedAt) {
    this.processedAt = processedAt;
  }
}


