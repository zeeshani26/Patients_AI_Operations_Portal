package com.pm.aiservice.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CustomHealthIndicator implements HealthIndicator {

  @Value("${ai.use-openai:false}")
  private boolean useOpenAI;

  @Override
  public Health health() {
    Health.Builder builder = Health.up()
        .withDetail("service", "ai-service")
        .withDetail("status", "operational")
        .withDetail("ai-enabled", useOpenAI ? "OpenAI" : "Rule-based");

    return builder.build();
  }
}


