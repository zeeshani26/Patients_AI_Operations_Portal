package com.pm.billingservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
public class BillingAccount {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true)
  private String accountId;

  @Column(nullable = false)
  private String patientId;

  @Column(nullable = false)
  private String patientName;

  @Column(nullable = false)
  private String patientEmail;

  @Column(nullable = false)
  private String status; // ACTIVE, SUSPENDED, CLOSED

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal balance = BigDecimal.ZERO;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
    if (accountId == null) {
      accountId = "BILL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
  }

  @jakarta.persistence.PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  // Getters and Setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getAccountId() {
    return accountId;
  }

  public void setAccountId(String accountId) {
    this.accountId = accountId;
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

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
    onUpdate();
  }

  public BigDecimal getBalance() {
    return balance;
  }

  public void setBalance(BigDecimal balance) {
    this.balance = balance;
    onUpdate();
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}

