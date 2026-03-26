package com.pm.billingservice.grpc;

import billing.BillingRequest;
import billing.BillingResponse;
import billing.BillingServiceGrpc.BillingServiceImplBase;
import com.pm.billingservice.model.BillingAccount;
import com.pm.billingservice.service.BillingAccountService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@GrpcService
@Service
public class BillingGrpcService extends BillingServiceImplBase {

  private static final Logger log = LoggerFactory.getLogger(
      BillingGrpcService.class);

  private final BillingAccountService billingAccountService;

  public BillingGrpcService(BillingAccountService billingAccountService) {
    this.billingAccountService = billingAccountService;
  }

  @Override
  public void createBillingAccount(BillingRequest billingRequest,
      StreamObserver<BillingResponse> responseObserver) {

    try {
      log.info("createBillingAccount request received for patient: {}", 
          billingRequest.getPatientId());

      // Create billing account with proper business logic
      BillingAccount account = billingAccountService.createBillingAccount(
          billingRequest.getPatientId(),
          billingRequest.getName(),
          billingRequest.getEmail()
      );

      BillingResponse response = BillingResponse.newBuilder()
          .setAccountId(account.getAccountId())
          .setStatus(account.getStatus())
          .setBalance(account.getBalance().doubleValue())
          .build();

      log.info("Successfully created billing account {} for patient {}", 
          account.getAccountId(), billingRequest.getPatientId());

      responseObserver.onNext(response);
      responseObserver.onCompleted();

    } catch (Exception e) {
      log.error("Error creating billing account for patient {}: {}", 
          billingRequest.getPatientId(), e.getMessage(), e);
      
      responseObserver.onError(
          Status.INTERNAL
              .withDescription("Failed to create billing account: " + e.getMessage())
              .asRuntimeException()
      );
    }
  }
}
