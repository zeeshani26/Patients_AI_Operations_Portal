package com.pm.billingservice.service;

import com.pm.billingservice.model.BillingAccount;
import com.pm.billingservice.repository.BillingAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingAccountServiceTest {

  @Mock
  private BillingAccountRepository repository;

  @InjectMocks
  private BillingAccountService service;

  private BillingAccount testAccount;

  @BeforeEach
  void setUp() {
    testAccount = new BillingAccount();
    testAccount.setId(UUID.randomUUID());
    testAccount.setAccountId("BILL-12345");
    testAccount.setPatientId("patient-123");
    testAccount.setPatientName("John Doe");
    testAccount.setPatientEmail("john@example.com");
    testAccount.setStatus("ACTIVE");
    testAccount.setBalance(BigDecimal.ZERO);
  }

  @Test
  void createBillingAccount_ShouldCreateNewAccount() {
    // Given
    when(repository.findByPatientId("patient-123")).thenReturn(Optional.empty());
    when(repository.save(any(BillingAccount.class))).thenReturn(testAccount);

    // When
    BillingAccount result = service.createBillingAccount(
        "patient-123", "John Doe", "john@example.com");

    // Then
    assertNotNull(result);
    assertEquals("ACTIVE", result.getStatus());
    assertEquals(BigDecimal.ZERO, result.getBalance());
    verify(repository).save(any(BillingAccount.class));
  }

  @Test
  void createBillingAccount_ShouldReturnExistingAccount() {
    // Given
    when(repository.findByPatientId("patient-123")).thenReturn(Optional.of(testAccount));

    // When
    BillingAccount result = service.createBillingAccount(
        "patient-123", "John Doe", "john@example.com");

    // Then
    assertNotNull(result);
    assertEquals(testAccount.getAccountId(), result.getAccountId());
    verify(repository, never()).save(any(BillingAccount.class));
  }

  @Test
  void updateBalance_ShouldUpdateBalanceSuccessfully() {
    // Given
    BigDecimal amount = new BigDecimal("100.50");
    when(repository.findByAccountId("BILL-12345")).thenReturn(Optional.of(testAccount));
    when(repository.save(any(BillingAccount.class))).thenReturn(testAccount);

    // When
    BillingAccount result = service.updateBalance("BILL-12345", amount);

    // Then
    assertNotNull(result);
    verify(repository).save(any(BillingAccount.class));
  }

  @Test
  void updateBalance_ShouldThrowExceptionWhenAccountNotFound() {
    // Given
    when(repository.findByAccountId("BILL-99999")).thenReturn(Optional.empty());

    // When & Then
    assertThrows(RuntimeException.class, 
        () -> service.updateBalance("BILL-99999", new BigDecimal("100")));
  }

  @Test
  void updateStatus_ShouldUpdateStatusSuccessfully() {
    // Given
    when(repository.findByAccountId("BILL-12345")).thenReturn(Optional.of(testAccount));
    when(repository.save(any(BillingAccount.class))).thenReturn(testAccount);

    // When
    BillingAccount result = service.updateStatus("BILL-12345", "SUSPENDED");

    // Then
    assertNotNull(result);
    verify(repository).save(any(BillingAccount.class));
  }
}


