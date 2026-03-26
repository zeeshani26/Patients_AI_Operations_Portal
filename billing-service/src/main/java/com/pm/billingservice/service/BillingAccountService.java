package com.pm.billingservice.service;

import com.pm.billingservice.model.BillingAccount;
import com.pm.billingservice.repository.BillingAccountRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingAccountService {

  private static final Logger log = LoggerFactory.getLogger(BillingAccountService.class);
  private final BillingAccountRepository repository;

  public BillingAccountService(BillingAccountRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public BillingAccount createBillingAccount(String patientId, String name, String email) {
    log.info("Creating billing account for patient: {}", patientId);

    // Check if account already exists
    Optional<BillingAccount> existing = repository.findByPatientId(patientId);
    if (existing.isPresent()) {
      log.warn("Billing account already exists for patient: {}", patientId);
      return existing.get();
    }

    BillingAccount account = new BillingAccount();
    account.setPatientId(patientId);
    account.setPatientName(name);
    account.setPatientEmail(email);
    account.setStatus("ACTIVE");
    account.setBalance(BigDecimal.ZERO);

    BillingAccount saved = repository.save(account);
    log.info("Created billing account {} for patient {}", saved.getAccountId(), patientId);
    return saved;
  }

  @Transactional
  public Optional<BillingAccount> getByPatientId(String patientId) {
    return repository.findByPatientId(patientId);
  }

  @Transactional
  public Optional<BillingAccount> getByAccountId(String accountId) {
    return repository.findByAccountId(accountId);
  }

  @Transactional
  public BillingAccount updateBalance(String accountId, BigDecimal amount) {
    BillingAccount account = repository.findByAccountId(accountId)
        .orElseThrow(() -> new RuntimeException("Billing account not found: " + accountId));

    BigDecimal oldBalance = account.getBalance();
    BigDecimal newBalance = oldBalance.add(amount);
    account.setBalance(newBalance);
    // @PreUpdate will handle updatedAt

    log.info("Updated balance for account {}: {} -> {}", accountId, oldBalance, newBalance);
    return repository.save(account);
  }

  @Transactional
  public BillingAccount updateStatus(String accountId, String status) {
    BillingAccount account = repository.findByAccountId(accountId)
        .orElseThrow(() -> new RuntimeException("Billing account not found: " + accountId));

    account.setStatus(status);
    // @PreUpdate will handle updatedAt

    log.info("Updated status for account {}: {}", accountId, status);
    return repository.save(account);
  }
}

