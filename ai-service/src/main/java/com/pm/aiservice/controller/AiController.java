package com.pm.aiservice.controller;

import com.pm.aiservice.dto.PatientRiskAssessmentRequest;
import com.pm.aiservice.dto.PatientRiskAssessmentResponse;
import com.pm.aiservice.service.AiPredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
@Tag(name = "AI Service", description = "AI-powered patient analytics and predictions")
public class AiController {

  private final AiPredictionService aiPredictionService;

  public AiController(AiPredictionService aiPredictionService) {
    this.aiPredictionService = aiPredictionService;
  }

  @PostMapping("/assess-risk")
  @Operation(summary = "Assess patient health risk using AI")
  public ResponseEntity<PatientRiskAssessmentResponse> assessPatientRisk(
      @Valid @RequestBody PatientRiskAssessmentRequest request) {
    
    PatientRiskAssessmentResponse response = aiPredictionService.assessPatientRisk(request);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/detect-anomalies")
  @Operation(summary = "Detect anomalies in patient data")
  public ResponseEntity<PatientRiskAssessmentResponse> detectAnomalies(
      @Valid @RequestBody PatientRiskAssessmentRequest request) {
    
    PatientRiskAssessmentResponse response = aiPredictionService.detectAnomalies(
        request.getPatientId(), request);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/health")
  @Operation(summary = "AI service health check")
  public ResponseEntity<String> health() {
    return ResponseEntity.ok("AI Service is operational");
  }
}


