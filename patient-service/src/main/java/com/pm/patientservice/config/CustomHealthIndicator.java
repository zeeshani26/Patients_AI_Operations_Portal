package com.pm.patientservice.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class CustomHealthIndicator implements HealthIndicator {

  @Override
  public Health health() {
    // Add custom health checks here
    // For example: check database connectivity, external service availability, etc.
    return Health.up()
        .withDetail("service", "patient-service")
        .withDetail("status", "operational")
        .build();
  }
}


