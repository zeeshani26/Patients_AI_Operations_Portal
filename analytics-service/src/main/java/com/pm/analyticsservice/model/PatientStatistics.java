package com.pm.analyticsservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "patient_statistics")
public class PatientStatistics {

  @Id
  private String id = "GLOBAL_STATS";

  @Column(nullable = false)
  private Long totalPatients = 0L;

  @Column(nullable = false)
  private Long patientsCreatedToday = 0L;

  @Column(nullable = false)
  private Long patientsCreatedThisWeek = 0L;

  @Column(nullable = false)
  private Long patientsCreatedThisMonth = 0L;

  @Column(nullable = false)
  private LocalDateTime lastUpdated = LocalDateTime.now();

  // Getters and Setters
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Long getTotalPatients() {
    return totalPatients;
  }

  public void setTotalPatients(Long totalPatients) {
    this.totalPatients = totalPatients;
    this.lastUpdated = LocalDateTime.now();
  }

  public Long getPatientsCreatedToday() {
    return patientsCreatedToday;
  }

  public void setPatientsCreatedToday(Long patientsCreatedToday) {
    this.patientsCreatedToday = patientsCreatedToday;
    this.lastUpdated = LocalDateTime.now();
  }

  public Long getPatientsCreatedThisWeek() {
    return patientsCreatedThisWeek;
  }

  public void setPatientsCreatedThisWeek(Long patientsCreatedThisWeek) {
    this.patientsCreatedThisWeek = patientsCreatedThisWeek;
    this.lastUpdated = LocalDateTime.now();
  }

  public Long getPatientsCreatedThisMonth() {
    return patientsCreatedThisMonth;
  }

  public void setPatientsCreatedThisMonth(Long patientsCreatedThisMonth) {
    this.patientsCreatedThisMonth = patientsCreatedThisMonth;
    this.lastUpdated = LocalDateTime.now();
  }

  public LocalDateTime getLastUpdated() {
    return lastUpdated;
  }

  public void setLastUpdated(LocalDateTime lastUpdated) {
    this.lastUpdated = lastUpdated;
  }
}


