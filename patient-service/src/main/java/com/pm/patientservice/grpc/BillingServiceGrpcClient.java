package com.pm.patientservice.grpc;

import billing.BillingRequest;
import billing.BillingResponse;
import billing.BillingServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class BillingServiceGrpcClient {

  private static final Logger log = LoggerFactory.getLogger(
      BillingServiceGrpcClient.class);
  private final BillingServiceGrpc.BillingServiceBlockingStub blockingStub;

  public BillingServiceGrpcClient(
      @Value("${billing.service.address:localhost}") String serverAddress,
      @Value("${billing.service.grpc.port:9001}") int serverPort) {

    log.info("Connecting to Billing Service GRPC service at {}:{}",
        serverAddress, serverPort);

    ManagedChannel channel = ManagedChannelBuilder.forAddress(serverAddress,
        serverPort).usePlaintext().build();

    blockingStub = BillingServiceGrpc.newBlockingStub(channel);
  }

  @CircuitBreaker(name = "billingService", fallbackMethod = "createBillingAccountFallback")
  @Retry(name = "billingService")
  @TimeLimiter(name = "billingService")
  public CompletableFuture<BillingResponse> createBillingAccountAsync(
      String patientId, String name, String email) {
    
    return CompletableFuture.supplyAsync(() -> {
      try {
        BillingRequest request = BillingRequest.newBuilder()
            .setPatientId(patientId)
            .setName(name)
            .setEmail(email)
            .build();

        BillingResponse response = blockingStub.createBillingAccount(request);
        log.info("Received response from billing service via GRPC: {}", response);
        return response;
      } catch (StatusRuntimeException e) {
        log.error("gRPC error calling billing service: {}", e.getStatus());
        throw new RuntimeException("Failed to create billing account", e);
      }
    });
  }

  @CircuitBreaker(name = "billingService", fallbackMethod = "createBillingAccountFallback")
  @Retry(name = "billingService")
  public BillingResponse createBillingAccount(String patientId, String name,
      String email) {

    try {
      BillingRequest request = BillingRequest.newBuilder()
          .setPatientId(patientId)
          .setName(name)
          .setEmail(email)
          .build();

      BillingResponse response = blockingStub.createBillingAccount(request);
      log.info("Received response from billing service via GRPC: {}", response);
      return response;
    } catch (StatusRuntimeException e) {
      log.error("gRPC error calling billing service: {}", e.getStatus());
      throw new RuntimeException("Failed to create billing account", e);
    }
  }

  // Fallback method for circuit breaker
  private BillingResponse createBillingAccountFallback(
      String patientId, String name, String email, Exception ex) {
    log.warn("Circuit breaker fallback triggered for patient {}: {}", patientId, ex.getMessage());
    // Return a default response or throw a custom exception
    // In production, you might want to queue this for later processing
    return BillingResponse.newBuilder()
        .setAccountId("PENDING")
        .setStatus("PENDING")
        .build();
  }
}
