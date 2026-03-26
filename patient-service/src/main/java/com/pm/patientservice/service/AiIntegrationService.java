package com.pm.patientservice.service;

import com.pm.patientservice.model.Patient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class AiIntegrationService {

  private static final Logger log = LoggerFactory.getLogger(AiIntegrationService.class);
  private final RestTemplate restTemplate;
  private final String aiServiceUrl;

  public AiIntegrationService(
      RestTemplate restTemplate,
      @Value("${ai.service.url:http://ai-service:4003}") String aiServiceUrl) {
    this.restTemplate = restTemplate;
    this.aiServiceUrl = aiServiceUrl;
  }

  public void triggerRiskAssessment(Patient patient) {
    try {
      log.info("Triggering AI risk assessment for patient: {}", patient.getId());

      Map<String, Object> request = new HashMap<>();
      request.put("patientId", patient.getId().toString());
      request.put("name", patient.getName());
      request.put("email", patient.getEmail());
      request.put("dateOfBirth", patient.getDateOfBirth().toString());
      request.put("address", patient.getAddress());

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);

      HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

      ResponseEntity<String> response = restTemplate.postForEntity(
          aiServiceUrl + "/ai/assess-risk",
          entity,
          String.class
      );

      if (response.getStatusCode().is2xxSuccessful()) {
        log.info("Successfully triggered AI risk assessment for patient: {}", patient.getId());
      } else {
        log.warn("AI risk assessment returned status: {} for patient: {}",
            response.getStatusCode(), patient.getId());
      }
    } catch (Exception e) {
      log.error("Error triggering AI risk assessment for patient {}: {}",
          patient.getId(), e.getMessage(), e);
      // Don't fail patient creation if AI service is unavailable
    }
  }
}


