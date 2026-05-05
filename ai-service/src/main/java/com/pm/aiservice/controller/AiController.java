package com.pm.aiservice.controller;

import com.pm.aiservice.dto.PatientRiskAssessmentRequest;
import com.pm.aiservice.dto.PatientRiskAssessmentResponse;
import com.pm.aiservice.dto.PatientPredictionDTO;
import com.pm.aiservice.dto.CausalGuardrailRequestDTO;
import com.pm.aiservice.dto.CausalGuardrailResponseDTO;
import com.pm.aiservice.dto.ChatMessageRequestDTO;
import com.pm.aiservice.dto.ChatMessageResponseDTO;
import com.pm.aiservice.dto.ExperimentRunRequestDTO;
import com.pm.aiservice.dto.ExperimentRunResponseDTO;
import com.pm.aiservice.dto.ExperimentScenarioSuiteResponseDTO;
import com.pm.aiservice.dto.ExperimentSummaryDTO;
import com.pm.aiservice.dto.InterventionAnalysisRequestDTO;
import com.pm.aiservice.dto.InterventionAnalysisResponseDTO;
import com.pm.aiservice.dto.ModelComparisonDTO;
import com.pm.aiservice.service.AiPredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

  @GetMapping("/predictions/{patientId}")
  @Operation(summary = "Fetch stored AI predictions for a patient")
  public ResponseEntity<List<PatientPredictionDTO>> getPredictionsForPatient(
      @PathVariable String patientId,
      @RequestParam(required = false) String predictionType) {
    if (predictionType == null || predictionType.isBlank()) {
      return ResponseEntity.ok(aiPredictionService.getPredictionsByPatientId(patientId));
    }
    return ResponseEntity.ok(
        aiPredictionService.getPredictionsByPatientIdAndType(patientId, predictionType));
  }

  @PostMapping("/analyze-intervention")
  @Operation(summary = "Run counterfactual intervention analysis")
  public ResponseEntity<InterventionAnalysisResponseDTO> analyzeIntervention(
      @Valid @RequestBody InterventionAnalysisRequestDTO request) {
    return ResponseEntity.ok(aiPredictionService.analyzeIntervention(request));
  }

  @PostMapping("/causal-guardrail/decision")
  @Operation(summary = "Select resilience fallback policy using causal guardrail logic")
  public ResponseEntity<CausalGuardrailResponseDTO> decideGuardrail(
      @RequestBody CausalGuardrailRequestDTO request) {
    return ResponseEntity.ok(aiPredictionService.decideCausalGuardrail(request));
  }

  @GetMapping("/model-comparison")
  @Operation(summary = "Get model performance versus reliability baseline")
  public ResponseEntity<List<ModelComparisonDTO>> getModelComparison() {
    return ResponseEntity.ok(aiPredictionService.getModelComparisonBaseline());
  }

  @PostMapping("/experiments/log")
  @Operation(summary = "Log a model experiment run for thesis evidence")
  public ResponseEntity<ExperimentRunResponseDTO> logExperimentRun(
      @Valid @RequestBody ExperimentRunRequestDTO request) {
    return ResponseEntity.ok(aiPredictionService.logExperimentRun(request));
  }

  @GetMapping("/experiments/summary")
  @Operation(summary = "Get aggregated experiment evidence summary")
  public ResponseEntity<ExperimentSummaryDTO> getExperimentSummary() {
    return ResponseEntity.ok(aiPredictionService.getExperimentSummary());
  }

  @PostMapping("/experiments/run-scenarios")
  @Operation(summary = "Execute predefined resilience scenarios and log evidence")
  public ResponseEntity<ExperimentScenarioSuiteResponseDTO> runScenarioSuite() {
    return ResponseEntity.ok(aiPredictionService.runScenarioSuite());
  }

  @PostMapping("/chat")
  @Operation(summary = "Conversational assistant endpoint for UI chatbot")
  public ResponseEntity<ChatMessageResponseDTO> chat(@Valid @RequestBody ChatMessageRequestDTO request) {
    return ResponseEntity.ok(aiPredictionService.chatWithAssistant(request));
  }

  @GetMapping("/health")
  @Operation(summary = "AI service health check")
  public ResponseEntity<String> health() {
    return ResponseEntity.ok("AI Service is operational");
  }
}


