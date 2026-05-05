package com.pm.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pm.aiservice.dto.PatientRiskAssessmentRequest;
import com.pm.aiservice.dto.PatientRiskAssessmentResponse;
import com.pm.aiservice.model.PatientPrediction;
import com.pm.aiservice.repository.PatientPredictionRepository;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import com.pm.aiservice.dto.CausalEdgeDTO;
import com.pm.aiservice.dto.CausalGuardrailRequestDTO;
import com.pm.aiservice.dto.CausalGuardrailResponseDTO;
import com.pm.aiservice.dto.ChatMessageRequestDTO;
import com.pm.aiservice.dto.ChatMessageResponseDTO;
import com.pm.aiservice.dto.CausalGraphDTO;
import com.pm.aiservice.dto.CausalNodeDTO;
import com.pm.aiservice.dto.ExperimentRunRequestDTO;
import com.pm.aiservice.dto.ExperimentRunResponseDTO;
import com.pm.aiservice.dto.ExperimentScenarioSuiteResponseDTO;
import com.pm.aiservice.dto.ExperimentSummaryDTO;
import com.pm.aiservice.dto.InterventionAnalysisRequestDTO;
import com.pm.aiservice.dto.InterventionAnalysisResponseDTO;
import com.pm.aiservice.dto.InterventionResultDTO;
import com.pm.aiservice.dto.ModelComparisonDTO;
import com.pm.aiservice.dto.PatientPredictionDTO;
import com.pm.aiservice.model.ExperimentRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pm.aiservice.repository.ExperimentRunRepository;

@Service
public class AiPredictionService {

  private static final Logger log = LoggerFactory.getLogger(AiPredictionService.class);
  private final PatientPredictionRepository repository;
  private final ExperimentRunRepository experimentRunRepository;
  private final LongitudinalDatasetService datasetService;
  private final GeminiChatService geminiChatService;
  private final OpenAiService openAiService;
  private final String mlMetricsPath;
  private final ObjectMapper objectMapper;
  private final Counter predictionCounter;
  private final Counter predictionErrorCounter;
  private final boolean useOpenAI;

  public AiPredictionService(
      PatientPredictionRepository repository,
      ExperimentRunRepository experimentRunRepository,
      LongitudinalDatasetService datasetService,
      GeminiChatService geminiChatService,
      @Value("${ai.ml.metrics.path:../model-comparison-evidence/ml_model_metrics.json}") String mlMetricsPath,
      @Value("${openai.api.key:}") String apiKey,
      @Value("${ai.use-openai:false}") boolean useOpenAI,
      MeterRegistry meterRegistry) {
    this.repository = repository;
    this.experimentRunRepository = experimentRunRepository;
    this.datasetService = datasetService;
    this.geminiChatService = geminiChatService;
    this.mlMetricsPath = mlMetricsPath;
    this.objectMapper = new ObjectMapper();
    this.useOpenAI = useOpenAI && apiKey != null && !apiKey.isEmpty();
    this.openAiService = this.useOpenAI ? new OpenAiService(apiKey) : null;
    this.predictionCounter = Counter.builder("ai.predictions.total")
        .description("Total number of AI predictions made")
        .register(meterRegistry);
    this.predictionErrorCounter = Counter.builder("ai.predictions.errors")
        .description("Number of errors in AI predictions")
        .register(meterRegistry);
  }

  @CircuitBreaker(name = "aiService", fallbackMethod = "assessPatientRiskFallback")
  @Retry(name = "aiService")
  @Transactional
  public PatientRiskAssessmentResponse assessPatientRisk(PatientRiskAssessmentRequest request) {
    log.info("Assessing patient risk for patient: {}", request.getPatientId());

    try {
      // Resilience demo hook: triggers CircuitBreaker fallback path.
      // If you're not doing the resilience demonstration, do NOT use this patientId value.
      if ("DEMO_FORCE_FAILURE".equalsIgnoreCase(request.getPatientId())) {
        throw new RuntimeException("Forced failure for resilience demonstration");
      }

      // Compute the rule-based causal structure first, then optionally replace
      // the free-text assessment with an OpenAI-generated one.
      PatientRiskAssessmentResponse response = assessRiskWithRuleBasedModel(request);

      if (useOpenAI && openAiService != null) {
        String openAIAssessmentText = assessRiskWithOpenAIText(request);
        if (openAIAssessmentText != null && !openAIAssessmentText.isBlank()) {
          response.setAssessment(openAIAssessmentText);
          response.setConfidenceScore(0.9);
        }
      }

      // Save prediction to database
      savePrediction(request.getPatientId(), "RISK_ASSESSMENT", 
          response.getAssessment(), response.getConfidenceScore());

      predictionCounter.increment();
      return response;

    } catch (Exception e) {
      predictionErrorCounter.increment();
      log.error("Error assessing patient risk for patient {}: {}", 
          request.getPatientId(), e.getMessage(), e);
      throw new RuntimeException("Failed to assess patient risk", e);
    }
  }

  private String assessRiskWithOpenAIText(PatientRiskAssessmentRequest request) {
    try {
      String prompt = buildRiskAssessmentPrompt(request);

      ChatMessage systemMessage = new ChatMessage("system",
          "You are a medical AI assistant that assesses patient risk levels. " +
          "Provide risk assessments in JSON format with riskLevel (LOW/MEDIUM/HIGH/CRITICAL), " +
          "riskScore (0.0-1.0), assessment (detailed text), riskFactors (array), " +
          "recommendations (array), and confidenceScore (0.0-1.0).");

      ChatMessage userMessage = new ChatMessage("user", prompt);

      ChatCompletionRequest chatRequest = ChatCompletionRequest.builder()
          .model("gpt-3.5-turbo")
          .messages(Arrays.asList(systemMessage, userMessage))
          .temperature(0.3)
          .maxTokens(500)
          .build();

      String responseText = openAiService.createChatCompletion(chatRequest)
          .getChoices().get(0).getMessage().getContent();

      return responseText;

    } catch (Exception e) {
      log.warn("OpenAI API call failed, falling back to rule-based model: {}", e.getMessage());
      return null;
    }
  }

  private static class RuleBasedRisk {
    private final double riskScoreRaw;
    private final String riskLevel;
    private final List<String> riskFactors;
    private final List<String> recommendations;
    private final double confidenceScore;
    private final Map<String, Double> factorContributions;
    private final int age;

    private RuleBasedRisk(
        double riskScoreRaw,
        String riskLevel,
        List<String> riskFactors,
        List<String> recommendations,
        double confidenceScore,
        Map<String, Double> factorContributions,
        int age) {
      this.riskScoreRaw = riskScoreRaw;
      this.riskLevel = riskLevel;
      this.riskFactors = riskFactors;
      this.recommendations = recommendations;
      this.confidenceScore = confidenceScore;
      this.factorContributions = factorContributions;
      this.age = age;
    }

    public double getRiskScoreRaw() {
      return riskScoreRaw;
    }

    public String getRiskLevel() {
      return riskLevel;
    }

    public List<String> getRiskFactors() {
      return riskFactors;
    }

    public List<String> getRecommendations() {
      return recommendations;
    }

    public double getConfidenceScore() {
      return confidenceScore;
    }

    public Map<String, Double> getFactorContributions() {
      return factorContributions;
    }

    public int getAge() {
      return age;
    }
  }

  private RuleBasedRisk computeRuleBasedRisk(PatientRiskAssessmentRequest request) {
    // Calculate age if not provided (chat payloads may omit DOB/age)
    int age;
    if (request.getAge() != null) {
      age = request.getAge();
    } else if (request.getDateOfBirth() != null) {
      age = java.time.Period.between(request.getDateOfBirth(), java.time.LocalDate.now()).getYears();
    } else {
      age = 45;
    }

    double riskScore = 0.0;
    List<String> riskFactors = new ArrayList<>();
    List<String> recommendations = new ArrayList<>();
    Map<String, Double> factorContributions = new HashMap<>();

    // Age-based risk
    if (age > 65) {
      riskScore += 0.3;
      riskFactors.add("Advanced age (65+)");
      factorContributions.put("Advanced age (65+)", 0.3);
      recommendations.add("Regular health checkups recommended");
    } else if (age > 50) {
      riskScore += 0.15;
      riskFactors.add("Middle age (50-65)");
      factorContributions.put("Middle age (50-65)", 0.15);
    }

    // Medical history risk
    if (request.getMedicalHistory() != null && !request.getMedicalHistory().isEmpty()) {
      String history = request.getMedicalHistory().toLowerCase();
      if (history.contains("diabetes") || history.contains("heart") ||
          history.contains("hypertension")) {
        riskScore += 0.25;
        riskFactors.add("Significant medical history");
        factorContributions.put("Significant medical history", 0.25);
        recommendations.add("Monitor existing conditions closely");
      }
    }

    // Medication risk
    if (request.getCurrentMedications() != null && !request.getCurrentMedications().isEmpty()) {
      riskScore += 0.1;
      riskFactors.add("On current medications");
      factorContributions.put("On current medications", 0.1);
      recommendations.add("Review medication interactions");
    }

    // Allergy risk
    if (request.getAllergies() != null && !request.getAllergies().isEmpty()) {
      riskScore += 0.15;
      riskFactors.add("Known allergies");
      factorContributions.put("Known allergies", 0.15);
      recommendations.add("Ensure allergy information is prominently displayed");
    }

    // Determine risk level
    String riskLevel;
    if (riskScore >= 0.7) {
      riskLevel = "CRITICAL";
    } else if (riskScore >= 0.5) {
      riskLevel = "HIGH";
    } else if (riskScore >= 0.3) {
      riskLevel = "MEDIUM";
    } else {
      riskLevel = "LOW";
    }

    double confidenceScore = 0.85; // Rule-based model confidence
    return new RuleBasedRisk(
        riskScore,
        riskLevel,
        riskFactors,
        recommendations,
        confidenceScore,
        factorContributions,
        age
    );
  }

  private String nodeIdFromLabel(String label) {
    return label.toLowerCase()
        .replaceAll("[^a-z0-9]+", "_")
        .replaceAll("^_+|_+$", "");
  }

  private PatientRiskAssessmentRequest cloneRequest(PatientRiskAssessmentRequest request) {
    PatientRiskAssessmentRequest clone = new PatientRiskAssessmentRequest();
    clone.setPatientId(request.getPatientId());
    clone.setName(request.getName());
    clone.setEmail(request.getEmail());
    clone.setDateOfBirth(request.getDateOfBirth());
    clone.setAddress(request.getAddress());
    clone.setAge(request.getAge());
    clone.setMedicalHistory(request.getMedicalHistory());
    clone.setCurrentMedications(request.getCurrentMedications());
    clone.setAllergies(request.getAllergies());
    return clone;
  }

  private InterventionResultDTO buildInterventionResult(
      PatientRiskAssessmentRequest baselineRequest,
      RuleBasedRisk baselineRisk,
      String interventionName,
      String targetField) {

    PatientRiskAssessmentRequest counterfactual = cloneRequest(baselineRequest);

    // Counterfactual modifications in input space.
    // We keep all other patient attributes the same.
    if ("medicalHistory".equals(targetField)) {
      counterfactual.setMedicalHistory("");
    } else if ("currentMedications".equals(targetField)) {
      counterfactual.setCurrentMedications("");
    } else if ("allergies".equals(targetField)) {
      counterfactual.setAllergies("");
    }

    RuleBasedRisk counterRisk = computeRuleBasedRisk(counterfactual);

    double baselineScoreClamped = Math.min(baselineRisk.getRiskScoreRaw(), 1.0);
    double counterScoreClamped = Math.min(counterRisk.getRiskScoreRaw(), 1.0);

    // Affected factors: contributions that change (remove/flip) in counterfactuals.
    List<String> affectedFactors = new ArrayList<>();
    Map<String, Double> baselineContrib = baselineRisk.getFactorContributions();
    Map<String, Double> counterContrib = counterRisk.getFactorContributions();
    for (String factorLabel : baselineContrib.keySet()) {
      double base = baselineContrib.getOrDefault(factorLabel, 0.0);
      double ctr = counterContrib.getOrDefault(factorLabel, 0.0);
      if (Math.abs(base - ctr) > 1e-9) {
        affectedFactors.add(factorLabel);
      }
    }

    InterventionResultDTO result = new InterventionResultDTO();
    result.setIntervention(interventionName);
    result.setBaselineRiskScore(baselineScoreClamped);
    result.setCounterfactualRiskScore(counterScoreClamped);
    result.setDeltaRiskScore(counterScoreClamped - baselineScoreClamped);
    result.setAffectedFactors(affectedFactors);
    return result;
  }

  private PatientRiskAssessmentResponse assessRiskWithRuleBasedModel(
      PatientRiskAssessmentRequest request) {
    
    log.debug("Using rule-based risk assessment model");
    
    PatientRiskAssessmentResponse response = new PatientRiskAssessmentResponse();
    response.setPatientId(request.getPatientId());
    response.setAssessedAt(LocalDateTime.now());

    RuleBasedRisk ruleRisk = computeRuleBasedRisk(request);

    double riskScoreClamped = Math.min(ruleRisk.getRiskScoreRaw(), 1.0);
    response.setRiskLevel(ruleRisk.getRiskLevel());
    response.setRiskScore(riskScoreClamped);
    response.setRiskFactors(ruleRisk.getRiskFactors());
    response.setRecommendations(ruleRisk.getRecommendations());
    response.setConfidenceScore(ruleRisk.getConfidenceScore());
    response.setFactorContributions(new LinkedHashMap<>(ruleRisk.getFactorContributions()));
    response.setConfidenceReason(buildConfidenceReason(request));

    // Generate assessment text (default; OpenAI mode may overwrite assessment later).
    String assessment = String.format(
        "Patient %s (Age: %d) has been assessed with %s risk level (Score: %.2f). " +
            "Key factors: %s. Recommendations: %s",
        request.getName(),
        ruleRisk.getAge(),
        ruleRisk.getRiskLevel(),
        ruleRisk.getRiskScoreRaw(),
        String.join(", ", ruleRisk.getRiskFactors()),
        String.join("; ", ruleRisk.getRecommendations()));
    response.setAssessment(assessment);

    // Build causal graph: factors -> riskScore
    CausalGraphDTO causalGraph = new CausalGraphDTO();
    List<CausalNodeDTO> nodes = new ArrayList<>();
    List<CausalEdgeDTO> edges = new ArrayList<>();

    // risk score node
    CausalNodeDTO riskScoreNode = new CausalNodeDTO();
    riskScoreNode.setId("risk_score");
    riskScoreNode.setLabel("Risk score");
    riskScoreNode.setType("RISK_SCORE");
    nodes.add(riskScoreNode);

    for (Map.Entry<String, Double> entry : ruleRisk.getFactorContributions().entrySet()) {
      String factorLabel = entry.getKey();
      Double contribution = entry.getValue();
      if (contribution == null || contribution <= 0.0) {
        continue;
      }
      String factorNodeId = nodeIdFromLabel(factorLabel);

      CausalNodeDTO factorNode = new CausalNodeDTO();
      factorNode.setId(factorNodeId);
      factorNode.setLabel(factorLabel);
      factorNode.setType("FACTOR");
      nodes.add(factorNode);

      CausalEdgeDTO edge = new CausalEdgeDTO();
      edge.setFrom(factorNodeId);
      edge.setTo("risk_score");
      edge.setWeight(contribution);
      edge.setDescription("Adds " + String.format("%.2f", contribution) + " to risk score");
      edges.add(edge);
    }

    causalGraph.setNodes(nodes);
    causalGraph.setEdges(edges);
    response.setCausalGraph(causalGraph);

    // Intervention analysis (counterfactuals)
    List<InterventionResultDTO> interventions = new ArrayList<>();
    interventions.add(buildInterventionResult(request, ruleRisk, "Remove medical history", "medicalHistory"));
    interventions.add(buildInterventionResult(request, ruleRisk, "Remove current medications", "currentMedications"));
    interventions.add(buildInterventionResult(request, ruleRisk, "Remove allergies", "allergies"));
    response.setInterventionResults(interventions);

    return response;
  }

  private String buildConfidenceReason(PatientRiskAssessmentRequest request) {
    List<String> missing = new ArrayList<>();
    if (request.getDateOfBirth() == null && request.getAge() == null) {
      missing.add("age/DOB");
    }
    if (request.getMedicalHistory() == null || request.getMedicalHistory().isBlank()) {
      missing.add("medical history detail");
    }
    if (request.getCurrentMedications() == null || request.getCurrentMedications().isBlank()) {
      missing.add("medication list");
    }
    if (request.getAllergies() == null || request.getAllergies().isBlank()) {
      missing.add("allergy detail");
    }
    if (missing.isEmpty()) {
      return "High confidence: key profile fields are present for rule-based scoring.";
    }
    return "Medium confidence: model used available profile fields, but missing " + String.join(", ", missing) + ".";
  }

  private void appendOptionalMlComparison(List<ModelComparisonDTO> results) {
    try {
      Path path = Paths.get(mlMetricsPath);
      if (!Files.exists(path)) {
        return;
      }
      JsonNode root = objectMapper.readTree(Files.readString(path));
      if (root == null || root.isMissingNode()) {
        return;
      }
      ModelComparisonDTO ml = new ModelComparisonDTO();
      ml.setModelName(root.path("modelName").asText("ML-Calibrated"));
      ml.setAvgLatencyMs(root.path("avgLatencyMs").asDouble(165.0));
      ml.setAvailabilityPct(root.path("availabilityPct").asDouble(97.0));
      ml.setExplainabilityScore(root.path("explainabilityScore").asDouble(7.5));
      ml.setStabilityUnderFaultPct(root.path("stabilityUnderFaultPct").asDouble(90.0));
      results.add(ml);
      log.info("Loaded optional ML comparison metrics from {}", path.toAbsolutePath());
    } catch (Exception e) {
      log.warn("Failed to load optional ML metrics from {}: {}", mlMetricsPath, e.getMessage());
    }
  }

  private String buildRiskAssessmentPrompt(PatientRiskAssessmentRequest request) {
    int age = request.getAge() != null ? request.getAge() : 
        java.time.Period.between(request.getDateOfBirth(), java.time.LocalDate.now()).getYears();

    return String.format(
        "Assess the health risk level for the following patient:\n" +
        "Name: %s\n" +
        "Age: %d\n" +
        "Date of Birth: %s\n" +
        "Medical History: %s\n" +
        "Current Medications: %s\n" +
        "Allergies: %s\n\n" +
        "Provide a comprehensive risk assessment.",
        request.getName(), age, request.getDateOfBirth(),
        request.getMedicalHistory() != null ? request.getMedicalHistory() : "None",
        request.getCurrentMedications() != null ? request.getCurrentMedications() : "None",
        request.getAllergies() != null ? request.getAllergies() : "None");
  }

  private PatientRiskAssessmentResponse parseAIResponse(String patientId, String responseText) {
    // Simple JSON parsing (in production, use proper JSON library)
    PatientRiskAssessmentResponse response = new PatientRiskAssessmentResponse();
    response.setPatientId(patientId);
    response.setAssessedAt(LocalDateTime.now());
    
    // For now, extract key information from text response
    // In production, ensure OpenAI returns proper JSON
    response.setAssessment(responseText);
    response.setRiskLevel("MEDIUM"); // Default, would parse from JSON
    response.setRiskScore(0.5);
    response.setConfidenceScore(0.9);
    response.setRiskFactors(new ArrayList<>());
    response.setRecommendations(new ArrayList<>());
    
    return response;
  }

  private List<String> detectAnomalyFactors(PatientRiskAssessmentRequest request) {
    List<String> anomalies = new ArrayList<>();

    // Check for data inconsistencies
    if (request.getAge() != null) {
      int calculatedAge = java.time.Period.between(
          request.getDateOfBirth(), java.time.LocalDate.now()).getYears();
      if (Math.abs(request.getAge() - calculatedAge) > 1) {
        anomalies.add("Age mismatch between provided age and date of birth");
      }
    }

    // Check for suspicious patterns
    if (request.getEmail() != null && !request.getEmail().contains("@")) {
      anomalies.add("Invalid email format");
    }

    return anomalies;
  }

  @Cacheable(value = "anomalyDetection", key = "#patientId")
  public PatientRiskAssessmentResponse detectAnomalies(String patientId, 
      PatientRiskAssessmentRequest request) {
    log.info("Detecting anomalies for patient: {}", patientId);
    List<String> anomalies = detectAnomalyFactors(request);

    PatientRiskAssessmentResponse response = new PatientRiskAssessmentResponse();
    response.setPatientId(patientId);
    response.setAssessedAt(LocalDateTime.now());
    
    if (anomalies.isEmpty()) {
      response.setRiskLevel("LOW");
      response.setRiskScore(0.1);
      response.setAssessment("No anomalies detected in patient data");
    } else {
      response.setRiskLevel("MEDIUM");
      response.setRiskScore(0.4);
      response.setAssessment("Anomalies detected: " + String.join(", ", anomalies));
      response.setRiskFactors(anomalies);
    }
    
    response.setConfidenceScore(0.8);
    response.setRecommendations(new ArrayList<>());

    // Causal graph: anomalies -> anomaly risk score
    CausalGraphDTO causalGraph = new CausalGraphDTO();
    List<CausalNodeDTO> nodes = new ArrayList<>();
    List<CausalEdgeDTO> edges = new ArrayList<>();

    CausalNodeDTO riskScoreNode = new CausalNodeDTO();
    riskScoreNode.setId("risk_score");
    riskScoreNode.setLabel("Anomaly risk score");
    riskScoreNode.setType("RISK_SCORE");
    nodes.add(riskScoreNode);

    if (!anomalies.isEmpty()) {
      double baseline = 0.4;
      double base = 0.1;
      double extra = baseline - base;
      double perFactor = extra / anomalies.size();

      for (String anomalyFactor : anomalies) {
        String factorNodeId = nodeIdFromLabel(anomalyFactor);

        CausalNodeDTO factorNode = new CausalNodeDTO();
        factorNode.setId(factorNodeId);
        factorNode.setLabel(anomalyFactor);
        factorNode.setType("ANOMALY");
        nodes.add(factorNode);

        CausalEdgeDTO edge = new CausalEdgeDTO();
        edge.setFrom(factorNodeId);
        edge.setTo("risk_score");
        edge.setWeight(perFactor);
        edge.setDescription("Removing this anomaly reduces anomaly risk score");
        edges.add(edge);
      }
    }

    causalGraph.setNodes(nodes);
    causalGraph.setEdges(edges);
    response.setCausalGraph(causalGraph);

    // Intervention analysis (counterfactuals): fix each anomaly factor independently.
    List<InterventionResultDTO> interventionResults = new ArrayList<>();
    if (!anomalies.isEmpty()) {
      for (String anomalyFactor : anomalies) {
        PatientRiskAssessmentRequest counterfactual = cloneRequest(request);

        if ("Age mismatch between provided age and date of birth".equals(anomalyFactor)) {
          int calculatedAge = java.time.Period.between(
              request.getDateOfBirth(), java.time.LocalDate.now()).getYears();
          counterfactual.setAge(calculatedAge);
        }
        if ("Invalid email format".equals(anomalyFactor)) {
          counterfactual.setEmail("valid@example.com");
        }

        List<String> counterAnomalies = detectAnomalyFactors(counterfactual);
        double baselineScore = response.getRiskScore();
        double counterScore = counterAnomalies.isEmpty() ? 0.1 : 0.4;

        List<String> affectedFactors = new ArrayList<>();
        if (counterAnomalies.isEmpty()) {
          affectedFactors.addAll(anomalies);
        } else {
          // Only include factors that changed between baseline and counterfactual.
          for (String f : anomalies) {
            if (!counterAnomalies.contains(f)) {
              affectedFactors.add(f);
            }
          }
        }

        InterventionResultDTO result = new InterventionResultDTO();
        result.setIntervention("Fix: " + anomalyFactor);
        result.setBaselineRiskScore(baselineScore);
        result.setCounterfactualRiskScore(counterScore);
        result.setDeltaRiskScore(counterScore - baselineScore);
        result.setAffectedFactors(affectedFactors);
        interventionResults.add(result);
      }
    }
    response.setInterventionResults(interventionResults);

    savePrediction(patientId, "ANOMALY_DETECTION", 
        response.getAssessment(), response.getConfidenceScore());

    return response;
  }

  public List<PatientPredictionDTO> getPredictionsByPatientId(String patientId) {
    List<PatientPrediction> predictions = repository.findByPatientId(patientId);
    List<PatientPredictionDTO> dtos = new ArrayList<>();
    for (PatientPrediction prediction : predictions) {
      dtos.add(toPredictionDTO(prediction));
    }
    return dtos;
  }

  public List<PatientPredictionDTO> getPredictionsByPatientIdAndType(
      String patientId, String predictionType) {
    List<PatientPrediction> predictions =
        repository.findByPatientIdAndPredictionType(patientId, predictionType);
    List<PatientPredictionDTO> dtos = new ArrayList<>();
    for (PatientPrediction prediction : predictions) {
      dtos.add(toPredictionDTO(prediction));
    }
    return dtos;
  }

  public List<PatientPredictionDTO> getPredictionsByPredictionType(String predictionType) {
    List<PatientPrediction> predictions = repository.findByPredictionType(predictionType);
    List<PatientPredictionDTO> dtos = new ArrayList<>();
    for (PatientPrediction prediction : predictions) {
      dtos.add(toPredictionDTO(prediction));
    }
    return dtos;
  }

  public InterventionAnalysisResponseDTO analyzeIntervention(
      InterventionAnalysisRequestDTO request) {
    PatientRiskAssessmentRequest patient = request.getPatient();
    RuleBasedRisk baselineRisk = computeRuleBasedRisk(patient);
    double baselineScore = Math.min(baselineRisk.getRiskScoreRaw(), 1.0);

    String interventionType = request.getInterventionType() == null
        ? "UNKNOWN" : request.getInterventionType().trim().toUpperCase();
    String interventionValue = request.getInterventionValue() == null
        ? "" : request.getInterventionValue().trim();

    double delta;
    String recommendation;
    List<String> assumptions = new ArrayList<>();

    switch (interventionType) {
      case "INCREASE_EXERCISE":
        delta = -0.12;
        recommendation = "Increase weekly exercise to at least 150 minutes.";
        assumptions.add("Exercise has direct protective effect on cardiometabolic risk.");
        assumptions.add("No severe mobility limitation blocks intervention adherence.");
        break;
      case "OPTIMIZE_MEDICATION":
        delta = -0.10;
        recommendation = "Medication reconciliation and adherence program is recommended.";
        assumptions.add("Current treatment plan can be optimized without major adverse effects.");
        assumptions.add("Adherence improves after intervention.");
        break;
      case "SMOKING_CESSATION":
        delta = -0.15;
        recommendation = "Enroll in smoking cessation pathway and follow-up counseling.";
        assumptions.add("Smoking status is a causal contributor to adverse outcomes.");
        assumptions.add("Patient achieves sustained reduction in smoking exposure.");
        break;
      case "DELAY_TREATMENT":
        delta = +0.14;
        recommendation = "Avoid treatment delay for high-risk profile; prioritize early intervention.";
        assumptions.add("Treatment delay causally increases progression risk.");
        assumptions.add("No compensatory mitigation offsets delay-related harm.");
        break;
      default:
        delta = -0.05;
        recommendation = "Use this estimate as directional support; select a specific intervention type.";
        assumptions.add("Default synthetic intervention magnitude applied.");
        break;
    }

    LongitudinalDatasetService.CohortStats cohortStats = datasetService.getConditionStats(
        patient.getMedicalHistory());
    if (datasetService.isLoaded()) {
      double riskModifier = 0.8 + cohortStats.getAvgOutcomeRisk();
      double severityModifier = 1.0 + (Math.min(100.0, cohortStats.getAvgSeverity()) / 400.0);
      delta = delta * riskModifier * severityModifier;
      assumptions.add(String.format(
          "Dataset-calibrated using cohort n=%d, avgOutcomeRisk=%.3f, avgSeverity=%.1f, avgTreatmentIntensity=%.1f",
          cohortStats.getCount(),
          cohortStats.getAvgOutcomeRisk(),
          cohortStats.getAvgSeverity(),
          cohortStats.getAvgTreatmentIntensity()));
    } else {
      assumptions.add("Longitudinal dataset unavailable; using rule-based prior intervention magnitude.");
    }

    if (!interventionValue.isEmpty()) {
      assumptions.add("Intervention parameter value: " + interventionValue);
    }

    double counterfactualScore = Math.max(0.0, Math.min(1.0, baselineScore + delta));
    double relativeReduction = baselineScore <= 0.0001
        ? 0.0 : ((baselineScore - counterfactualScore) / baselineScore) * 100.0;

    InterventionAnalysisResponseDTO response = new InterventionAnalysisResponseDTO();
    response.setPatientId(patient.getPatientId());
    response.setInterventionType(interventionType);
    response.setInterventionValue(interventionValue);
    response.setBaselineRiskScore(baselineScore);
    response.setCounterfactualRiskScore(counterfactualScore);
    response.setDeltaRiskScore(counterfactualScore - baselineScore);
    response.setRelativeRiskReductionPct(relativeReduction);
    response.setRecommendation(recommendation);
    response.setAssumptions(assumptions);
    response.setConfidenceScore(0.78);
    return response;
  }

  public CausalGuardrailResponseDTO decideCausalGuardrail(CausalGuardrailRequestDTO request) {
    String suspectedCause;
    String selectedStrategy;
    String selectedModelMode;
    String rationale;
    double confidence;

    if (request.getDbLatencyMs() > 250) {
      suspectedCause = "DB_BOTTLENECK";
      selectedStrategy = "DB_DEGRADED_MODE";
      selectedModelMode = "RULE_BASED_FAST";
      rationale = "Database latency dominates; switch to low-dependency fast path.";
      confidence = 0.86;
    } else if (request.getAiLatencyMs() > 220) {
      suspectedCause = "AI_SERVICE_SLOWDOWN";
      selectedStrategy = "AI_FALLBACK_POLICY";
      selectedModelMode = "RULE_BASED_FAST";
      rationale = "AI latency exceeded threshold; preserve availability with deterministic fallback.";
      confidence = 0.83;
    } else if (request.getKafkaLag() > 5000) {
      suspectedCause = "ASYNC_PIPELINE_BACKLOG";
      selectedStrategy = "EVENTUAL_CONSISTENCY_MODE";
      selectedModelMode = "CAUSAL_HYBRID";
      rationale = "High Kafka lag detected; continue with causal hybrid while deferring non-critical writes.";
      confidence = 0.79;
    } else if (request.getTrafficLoadPct() > 85) {
      suspectedCause = "HIGH_TRAFFIC_LOAD";
      selectedStrategy = "LOAD_SHEDDING_AND_CACHE";
      selectedModelMode = "RULE_BASED_FAST";
      rationale = "System under burst load; prioritize throughput and graceful degradation.";
      confidence = 0.81;
    } else if (request.getErrorRatePct() > 4.0) {
      suspectedCause = "UNKNOWN_SYSTEM_INSTABILITY";
      selectedStrategy = "SAFE_MODE_DIAGNOSTIC";
      selectedModelMode = "RULE_BASED_FAST";
      rationale = "Error rate is elevated with no dominant latency signal; enter safe mode.";
      confidence = 0.72;
    } else {
      suspectedCause = "HEALTHY";
      selectedStrategy = "NORMAL_OPERATION";
      selectedModelMode = "CAUSAL_HYBRID";
      rationale = "No degradation threshold breached; maintain default causally-informed path.";
      confidence = 0.88;
    }

    CausalGuardrailResponseDTO response = new CausalGuardrailResponseDTO();
    response.setSuspectedCause(suspectedCause);
    response.setSelectedStrategy(selectedStrategy);
    response.setSelectedModelMode(selectedModelMode);
    response.setRationale(rationale);
    response.setConfidenceScore(confidence);
    return response;
  }

  public List<ModelComparisonDTO> getModelComparisonBaseline() {
    List<ModelComparisonDTO> results = new ArrayList<>();

    ModelComparisonDTO ruleOnly = new ModelComparisonDTO();
    ruleOnly.setModelName("Rule-Based");
    ruleOnly.setAvgLatencyMs(48.0);
    ruleOnly.setAvailabilityPct(99.4);
    ruleOnly.setExplainabilityScore(8.6);
    ruleOnly.setStabilityUnderFaultPct(89.0);
    results.add(ruleOnly);

    ModelComparisonDTO llmOnly = new ModelComparisonDTO();
    llmOnly.setModelName("LLM-Only");
    llmOnly.setAvgLatencyMs(410.0);
    llmOnly.setAvailabilityPct(94.7);
    llmOnly.setExplainabilityScore(6.8);
    llmOnly.setStabilityUnderFaultPct(72.0);
    results.add(llmOnly);

    ModelComparisonDTO causalHybrid = new ModelComparisonDTO();
    causalHybrid.setModelName("Causal-Hybrid");
    causalHybrid.setAvgLatencyMs(118.0);
    causalHybrid.setAvailabilityPct(98.8);
    causalHybrid.setExplainabilityScore(9.1);
    causalHybrid.setStabilityUnderFaultPct(93.0);
    results.add(causalHybrid);
    appendOptionalMlComparison(results);
    return results;
  }

  @Transactional
  public ExperimentRunResponseDTO logExperimentRun(ExperimentRunRequestDTO request) {
    ExperimentRun run = new ExperimentRun();
    run.setModelName(request.getModelName());
    run.setScenarioName(request.getScenarioName());
    run.setAvgLatencyMs(request.getAvgLatencyMs());
    run.setAvailabilityPct(request.getAvailabilityPct());
    run.setStabilityUnderFaultPct(request.getStabilityUnderFaultPct());
    run.setExplainabilityScore(request.getExplainabilityScore());
    run.setFallbackCorrectnessPct(request.getFallbackCorrectnessPct());
    run.setNotes(request.getNotes());

    ExperimentRun saved = experimentRunRepository.save(run);
    return toExperimentRunResponse(saved);
  }

  public ExperimentSummaryDTO getExperimentSummary() {
    List<ExperimentRun> allRuns = experimentRunRepository.findAll();
    Map<String, Aggregator> byModel = new HashMap<>();
    for (ExperimentRun run : allRuns) {
      Aggregator aggregator = byModel.computeIfAbsent(run.getModelName(), key -> new Aggregator());
      aggregator.accept(run);
    }

    List<ModelComparisonDTO> aggregated = new ArrayList<>();
    for (Map.Entry<String, Aggregator> entry : byModel.entrySet()) {
      String model = entry.getKey();
      Aggregator agg = entry.getValue();

      ModelComparisonDTO dto = new ModelComparisonDTO();
      dto.setModelName(model);
      dto.setAvgLatencyMs(agg.avgLatencyMs());
      dto.setAvailabilityPct(agg.avgAvailabilityPct());
      dto.setStabilityUnderFaultPct(agg.avgStabilityUnderFaultPct());
      dto.setExplainabilityScore(agg.avgExplainabilityScore());
      aggregated.add(dto);
    }

    List<ExperimentRunResponseDTO> recent = new ArrayList<>();
    for (ExperimentRun run : experimentRunRepository.findTop200ByOrderByCreatedAtDesc()) {
      recent.add(toExperimentRunResponse(run));
    }

    ExperimentSummaryDTO summary = new ExperimentSummaryDTO();
    summary.setTotalRuns(allRuns.size());
    summary.setAggregatedByModel(aggregated);
    summary.setRecentRuns(recent);
    return summary;
  }

  public ExperimentScenarioSuiteResponseDTO runScenarioSuite() {
    List<ExperimentRunResponseDTO> logged = new ArrayList<>();
    Map<String, String> scenarioStrategy = new LinkedHashMap<>();

    List<CausalGuardrailRequestDTO> scenarios = new ArrayList<>();
    List<String> names = new ArrayList<>();

    CausalGuardrailRequestDTO dbSpike = new CausalGuardrailRequestDTO();
    dbSpike.setAiLatencyMs(180);
    dbSpike.setDbLatencyMs(380);
    dbSpike.setKafkaLag(800);
    dbSpike.setErrorRatePct(1.8);
    dbSpike.setTrafficLoadPct(68.0);
    scenarios.add(dbSpike);
    names.add("DB latency spike");

    CausalGuardrailRequestDTO aiOverload = new CausalGuardrailRequestDTO();
    aiOverload.setAiLatencyMs(520);
    aiOverload.setDbLatencyMs(140);
    aiOverload.setKafkaLag(1200);
    aiOverload.setErrorRatePct(2.6);
    aiOverload.setTrafficLoadPct(74.0);
    scenarios.add(aiOverload);
    names.add("AI service overload");

    CausalGuardrailRequestDTO backlog = new CausalGuardrailRequestDTO();
    backlog.setAiLatencyMs(230);
    backlog.setDbLatencyMs(210);
    backlog.setKafkaLag(9300);
    backlog.setErrorRatePct(1.1);
    backlog.setTrafficLoadPct(62.0);
    scenarios.add(backlog);
    names.add("Event stream backlog");

    for (int i = 0; i < scenarios.size(); i++) {
      CausalGuardrailRequestDTO scenario = scenarios.get(i);
      String scenarioName = names.get(i);
      CausalGuardrailResponseDTO decision = decideCausalGuardrail(scenario);
      scenarioStrategy.put(scenarioName, decision.getSelectedStrategy());

      ExperimentRunRequestDTO request = new ExperimentRunRequestDTO();
      request.setModelName(
          "RULE_BASED_FAST".equalsIgnoreCase(decision.getSelectedModelMode())
              ? "Rule-Based"
              : "Causal-Hybrid");
      request.setScenarioName(scenarioName);
      request.setAvgLatencyMs(Math.max(scenario.getAiLatencyMs(), scenario.getDbLatencyMs()));
      request.setAvailabilityPct(Math.max(90.0, 99.5 - (scenario.getErrorRatePct() * 2.0)));
      request.setStabilityUnderFaultPct(Math.max(78.0, 97.0 - (scenario.getTrafficLoadPct() / 6.5)));
      request.setExplainabilityScore(
          "RULE_BASED_FAST".equalsIgnoreCase(decision.getSelectedModelMode()) ? 9.4 : 8.7);
      request.setFallbackCorrectnessPct(
          "NORMAL_OPERATION".equalsIgnoreCase(decision.getSelectedStrategy()) ? 88.0 : 94.0);
      request.setNotes(
          "Auto scenario suite run. Cause="
              + decision.getSuspectedCause()
              + ", strategy="
              + decision.getSelectedStrategy()
              + ", mode="
              + decision.getSelectedModelMode());
      logged.add(logExperimentRun(request));
    }

    ExperimentScenarioSuiteResponseDTO response = new ExperimentScenarioSuiteResponseDTO();
    response.setExecutedRuns(logged.size());
    response.setLoggedRuns(logged);
    response.setGuardrailStrategiesByScenario(scenarioStrategy);
    return response;
  }

  public ChatMessageResponseDTO chatWithAssistant(ChatMessageRequestDTO request) {
    String userMessage = request.getMessage() == null ? "" : request.getMessage().trim();
    String lower = normalizeChatLower(userMessage);
    PatientRiskAssessmentRequest patient = request.getPatientContext();

    ChatMessageResponseDTO response = new ChatMessageResponseDTO();
    response.setConfidence("MEDIUM");

    if (lower.isBlank()) {
      response.setAnswer("Please type a question. I can help with patient details, risk, anomalies, intervention what-if, guardrail behavior, and experiment metrics.");
      response.setActionHint("Try: 'What is the patient address?'");
      return finishChatResponse(response, userMessage, patient, false);
    }

    if (lower.contains("what can you do") || lower.contains("help") || lower.contains("features")) {
      response.setAnswer(
          "I can answer patient profile questions, explain risk and anomaly findings, run intervention what-if reasoning, explain guardrail strategy, and summarize experiment metrics.");
      response.setActionHint("Try: 'What is the patient address?', 'Assess risk and justify it', or 'How does guardrail work?'");
      return finishChatResponse(response, userMessage, patient, true);
    }

    if (patient != null) {
      if (lower.contains("address")) {
        response.setAnswer("Current patient address: " + safeText(patient.getAddress(), "not available") + ".");
        response.setActionHint("Ask for name, email, age, or risk interpretation next.");
        return finishChatResponse(response, userMessage, patient, true);
      }
      if (lower.contains("email")) {
        response.setAnswer("Current patient email: " + safeText(patient.getEmail(), "not available") + ".");
        response.setActionHint("Ask for address, DOB, or risk interpretation.");
        return finishChatResponse(response, userMessage, patient, true);
      }
      if (lower.contains("name") || lower.contains("who is the patient") || lower.contains("which patient")) {
        response.setAnswer("Active patient context: " + safeText(patient.getName(), "unknown")
            + " (" + safeText(patient.getPatientId(), "no-id") + ").");
        response.setActionHint("Use Patient Records to click a different row if needed.");
        return finishChatResponse(response, userMessage, patient, true);
      }
      if (lower.contains("age") || lower.contains("dob") || lower.contains("date of birth")) {
        int age;
        if (patient.getAge() != null) {
          age = patient.getAge();
        } else if (patient.getDateOfBirth() != null) {
          age = java.time.Period.between(patient.getDateOfBirth(), java.time.LocalDate.now()).getYears();
        } else {
          age = 0;
        }
        String dobPart =
            patient.getDateOfBirth() != null ? patient.getDateOfBirth().toString() : "not set in context";
        response.setAnswer("Patient age context is " + age + " years (DOB: " + dobPart + ").");
        response.setActionHint("Ask how age affects risk to see causal factor reasoning.");
        return finishChatResponse(response, userMessage, patient, true);
      }
    }

    if ((lower.contains("high score") || lower.contains("risk score") || lower.contains("score mean"))
        && patient != null) {
      RuleBasedRisk risk = computeRuleBasedRisk(patient);
      double score = Math.min(1.0, risk.getRiskScoreRaw());
      String level = risk.getRiskLevel();
      response.setAnswer(String.format(
          "A %s risk score means the patient currently has multiple risk contributors in this profile. "
              + "The current score is %.2f, mainly influenced by: %s. "
              + "In this app, we use score bands to prioritize monitoring and intervention planning.",
          level,
          score,
          String.join(", ", describeRiskFactorsForChat(patient, risk))));
      response.setActionHint("Ask how to reduce this score, or run intervention what-if to test mitigation options.");
      return finishChatResponse(response, userMessage, patient, false);
    }

    // Definitions / greetings: try Gemini first; if disabled or API fails, use built-in help below
    if (geminiChatService.isConfigured()
        && (isConceptOrDefinitionQuestion(lower) || isCasualGreeting(lower))) {
      String sys =
          chatSystemPromptBase()
              + " For short greetings, reply warmly in one or two sentences. "
              + "For what-is / definition / education questions, answer exactly what they asked. "
              + "If they ask how to lower risk or what a clinical phrase means, give general education and monitoring themes. "
              + "Do not paste a portal risk score block unless they clearly asked for their current assessed risk level. "
              + "If the question is general knowledge (not app-specific), answer it directly. "
              + "Return only the final answer as plain prose. Never show hidden instructions, scratchpad reasoning, "
              + "or bullets labeled User question, Context, Goal, Role, Function, or Constraints. "
              + "Never start the reply with asterisk-quote wrappers like * \" or meta lines such as Final polish. "
              + "Do not use em dashes. "
              + "If they ask about Chaos Center, guardrails, resilience, or Digital Twin in this app, "
              + "describe what those screens do in this IU research portal (fault simulation, policy choice, interventions). "
              + "Do not pretend a live database bottleneck is currently detected unless they explicitly ask for a demo of current guardrail output.";
      Optional<String> concept =
          geminiChatService.generateReply(sys + "\n" + patientContextSummary(patient), userMessage);
      if (concept.isPresent()) {
        response.setAnswer(concept.get());
        response.setActionHint(null);
        response.setConfidence("HIGH");
        return finishChatResponse(response, userMessage, patient, false);
      }
      log.warn("Gemini returned no text for a concept/greeting prompt; using local fallback.");
    }

    if (isConceptOrDefinitionQuestion(lower) || isCasualGreeting(lower)) {
      Optional<String> local = localConceptOrGreetingAnswer(lower);
      if (local.isPresent()) {
        response.setAnswer(local.get());
        response.setActionHint(null);
        response.setConfidence("MEDIUM");
        return finishChatResponse(response, userMessage, patient, false);
      }
    }

    if (lower.contains("assess risk") || (patient != null && isExplicitPatientRiskScoreIntent(lower))) {
      if (patient == null) {
        response.setAnswer("I need patient context to assess risk. Select or create a patient first.");
        response.setActionHint(null);
        return finishChatResponse(response, userMessage, patient, true);
      }
      RuleBasedRisk risk = computeRuleBasedRisk(patient);
      double score = Math.min(1.0, risk.getRiskScoreRaw());
      response.setAnswer(String.format(
          "For patient %s, current risk is %s (score %.2f). Top factors: %s.",
          patient.getName(),
          risk.getRiskLevel(),
          score,
          String.join(", ", describeRiskFactorsForChat(patient, risk))));
      response.setActionHint(null);
      return finishChatResponse(response, userMessage, patient, false);
    }
    if (lower.contains("anomal")) {
      if (patient == null) {
        response.setAnswer("I need patient context to detect anomalies.");
        response.setActionHint("Create/select a patient and ask anomaly check again.");
        return finishChatResponse(response, userMessage, patient, true);
      }
      List<String> anomalies = detectAnomalyFactors(patient);
      if (anomalies.isEmpty()) {
        response.setAnswer("No obvious anomalies detected for the current patient profile.");
      } else {
        response.setAnswer("Detected anomalies: " + String.join(", ", anomalies) + ".");
      }
      response.setActionHint("You can ask me to explain each anomaly impact.");
      return finishChatResponse(response, userMessage, patient, false);
    }
    if (lower.contains("intervention") || lower.contains("what-if") || lower.contains("counterfactual")) {
      if (patient == null) {
        response.setAnswer("I need patient context for intervention simulation.");
        response.setActionHint("Set active patient in Digital Twin and retry.");
        return finishChatResponse(response, userMessage, patient, true);
      }
      String intervention = "OPTIMIZE_MEDICATION";
      if (lower.contains("exercise")) intervention = "INCREASE_EXERCISE";
      if (lower.contains("smok")) intervention = "SMOKING_CESSATION";
      if (lower.contains("delay")) intervention = "DELAY_TREATMENT";

      InterventionAnalysisRequestDTO interventionRequest = new InterventionAnalysisRequestDTO();
      interventionRequest.setPatient(patient);
      interventionRequest.setInterventionType(intervention);
      interventionRequest.setInterventionValue("");
      InterventionAnalysisResponseDTO analysis = analyzeIntervention(interventionRequest);

      response.setAnswer(String.format(
          "Counterfactual result for %s: baseline %.2f, after intervention %.2f (delta %.2f). Recommendation: %s",
          intervention,
          analysis.getBaselineRiskScore(),
          analysis.getCounterfactualRiskScore(),
          analysis.getDeltaRiskScore(),
          analysis.getRecommendation()));
      response.setActionHint("Try another intervention keyword: exercise, smoking, medication, or delay.");
      return finishChatResponse(response, userMessage, patient, false);
    }
    // Canned operational demo only when user wants a simulation-style answer, not a definition
    if (wantsLiveGuardrailSimulation(lower)) {
      CausalGuardrailRequestDTO req = new CausalGuardrailRequestDTO();
      req.setAiLatencyMs(300);
      req.setDbLatencyMs(260);
      req.setKafkaLag(3000);
      req.setErrorRatePct(2.0);
      req.setTrafficLoadPct(75.0);
      CausalGuardrailResponseDTO decision = decideCausalGuardrail(req);
      String plainCause = plainCause(decision.getSuspectedCause());
      String plainStrategy = plainStrategy(decision.getSelectedStrategy());
      String plainMode = plainModelMode(decision.getSelectedModelMode());
      response.setAnswer(String.format(
          "Resilience behavior means how the system stays reliable during stress. "
              + "Right now, it suspects %s. So it switches to %s. "
              + "In simple terms, it uses %s to keep responses stable instead of failing.",
          plainCause,
          plainStrategy,
          plainMode));
      response.setActionHint("Use Chaos Center sliders to simulate failures and watch how this decision changes.");
      return finishChatResponse(response, userMessage, patient, false);
    }
    if (lower.contains("json")) {
      response.setAnswer("JSON is the raw transport format. The UI now converts responses into cards, summaries, and tables for business meaning.");
      response.setActionHint("Open Reports page to view interpreted summaries.");
      return finishChatResponse(response, userMessage, patient, false);
    }

    if (lower.contains("dataset") || lower.contains("data quality") || lower.contains("gibberish")) {
      response.setAnswer(
          "You do not need to replace the entire dataset for better chat quality. "
              + "Better results come from richer mapped patient context (vitals, severity, comorbidities) and clearer prompts.");
      response.setActionHint("I can next add dataset-to-patient context mapping if you want deeper answers.");
      return finishChatResponse(response, userMessage, patient, false);
    }

    String geminiSystem = chatSystemPromptBase() + "\n" + patientContextSummary(patient);
    Optional<String> gemini = geminiChatService.generateReply(geminiSystem, userMessage);
    if (gemini.isPresent()) {
      response.setAnswer(gemini.get());
      response.setActionHint(null);
      response.setConfidence("HIGH");
      return finishChatResponse(response, userMessage, patient, false);
    }

    if (useOpenAI && openAiService != null) {
      try {
        String context = patientContextSummary(patient);
        ChatMessage systemMessage = new ChatMessage(
            "system",
            chatSystemPromptBase() + " "
                + "If required data is unavailable, say exactly what is missing.");
        ChatMessage user = new ChatMessage("user", context + " User message: " + userMessage);
        ChatCompletionRequest chatRequest = ChatCompletionRequest.builder()
            .model("gpt-3.5-turbo")
            .messages(Arrays.asList(systemMessage, user))
            .temperature(0.2)
            .maxTokens(360)
            .build();
        String answer = openAiService.createChatCompletion(chatRequest)
            .getChoices().get(0).getMessage().getContent();
        response.setAnswer(answer);
        response.setActionHint("You can ask about this portal, risk, anomalies, interventions, or guardrail choices.");
        response.setConfidence("HIGH");
        return finishChatResponse(response, userMessage, patient, false);
      } catch (Exception e) {
        log.warn("Chat OpenAI call failed, falling back to local assistant rules: {}", e.getMessage());
      }
    }

    String inferred = patient != null
        ? "Using active patient " + safeText(patient.getName(), "unknown") + ", I can answer profile questions, risk, anomaly, intervention, and resilience topics."
        : "Select a patient in Patient Records for profile-aware answers.";
    response.setAnswer(
        "I could not generate a broader answer right now. "
            + inferred
            + " Please try again in a moment.");
    response.setActionHint("Try What is the patient address, Assess risk and justify it, Any anomalies, or Explain resilience.");
    return finishChatResponse(response, userMessage, patient, false);
  }

  /**
   * Optionally appends a Gemini rewrite of the verified local answer (same facts, friendlier prose).
   */
  private ChatMessageResponseDTO finishChatResponse(
      ChatMessageResponseDTO response,
      String userMessage,
      PatientRiskAssessmentRequest patient,
      boolean allowGeminiAugment) {
    if (!allowGeminiAugment || !geminiChatService.isAugmentEnabled()) {
      return response;
    }
    String augmentSystem =
        "You are a clinical-operations copilot for an IU Indianapolis research demo. "
            + "The user message includes a VERIFIED backend summary from rule-based or causal logic. "
            + "Write 2-4 short paragraphs in plain English that elaborate and clarify. "
            + "Do NOT contradict, remove, or change any numbers, names, or factual claims from the summary. "
            + "Do not diagnose or prescribe. Do not invent patient data beyond what appears in the summary. "
            + "Do not use em dashes. Do not output meta bullets labeled Context, Goal, or Constraints.";
    String augmentUser =
        "User question:\n"
            + userMessage
            + "\n\nVerified backend summary (preserve all facts in meaning):\n"
            + response.getAnswer()
            + "\n\nSuggested follow-up:\n"
            + (response.getActionHint() == null ? "" : response.getActionHint())
            + "\n\nPatient context for tone only (do not contradict if absent from summary):\n"
            + patientContextSummary(patient);
    geminiChatService
        .generateAugmentation(augmentSystem, augmentUser)
        .ifPresent(
            g -> {
              response.setAnswer(response.getAnswer() + "\n\n(Copilot note)\n" + g);
              response.setConfidence("HIGH");
            });
    return response;
  }

  /**
   * Built-in answers when Gemini is unavailable — keeps the demo usable without any cloud LLM.
   */
  private Optional<String> localConceptOrGreetingAnswer(String lower) {
    if (isCasualGreeting(lower)) {
      return Optional.of(
          "Hi, I am the AI copilot for this research portal. I can explain risk, anomalies, interventions, "
              + "and how Chaos Center and guardrails work here. Try asking what a guardrail is, or say assess my risk.");
    }
    if (lower.contains("guardrail")) {
      return Optional.of(
          "In this application, a **guardrail** is the resilience policy layer: when the stack shows stress "
              + "(slow AI, DB, Kafka lag, errors), the backend picks a strategy such as degraded DB mode, load shedding, "
              + "or a fast rule-based model so the API stays up. You can see a **simulated** decision in Chaos Center; "
              + "it is for demonstration, not a live production incident feed.");
    }
    if (lower.contains("chaos")) {
      return Optional.of(
          "**Chaos Center** is a page in this portal where you adjust fault-style sliders (latency, lag, errors, load) "
              + "to see how the **causal guardrail** would classify the situation and choose a fallback strategy. "
              + "It is a teaching tool for resilience behavior, not real chaos engineering against production.");
    }
    if (lower.contains("digital twin")) {
      return Optional.of(
          "**Digital Twin** here means the workflow that takes the active patient context and runs risk assessment, "
              + "anomaly checks, and counterfactual intervention what-if: a structured “twin” of decisions around "
              + "that patient for the demo.");
    }
    if (lower.contains("resilience") || lower.contains("portal") || lower.contains("this app")) {
      return Optional.of(
          "This **Patient AI Operations** portal ties together microservices (auth, patients, billing, analytics, AI) "
              + "behind an API gateway. The originality for your study is the **causal guardrail** (how to react under faults) "
              + "plus **intervention analysis** and experiment logging, all explorable from the nav tabs.");
    }
    if (isConceptOrDefinitionQuestion(lower)) {
      return Optional.of(
          "I can explain terms in this demo on request. Try: **What is Chaos Center?**, **What is a guardrail here?**, "
              + "or **What does a high risk score mean?**");
    }
    return Optional.empty();
  }

  private List<String> describeRiskFactorsForChat(
      PatientRiskAssessmentRequest patient, RuleBasedRisk risk) {
    List<String> readable = new ArrayList<>();
    for (String factor : risk.getRiskFactors()) {
      switch (factor) {
        case "Significant medical history":
          readable.add("medical history includes " + expandMedicalHistoryForChat(patient.getMedicalHistory()));
          break;
        case "On current medications":
          readable.add(
              "current treatment profile includes "
                  + expandMedicationForChat(patient.getCurrentMedications()));
          break;
        case "Known allergies":
          readable.add(
              "allergy profile indicates "
                  + expandAllergyForChat(patient.getAllergies()));
          break;
        case "Advanced age (65+)":
        case "Middle age (50-65)":
          readable.add("age-related risk contribution (" + factor + ")");
          break;
        default:
          readable.add(factor.toLowerCase());
      }
    }
    if (readable.isEmpty()) {
      readable.add("no major high-risk markers detected in available profile fields");
    }
    return readable;
  }

  private String expandMedicalHistoryForChat(String history) {
    String text = safeText(history, "documented chronic conditions requiring follow-up");
    String lower = text.toLowerCase();
    if (lower.contains("hypertension")) {
      return "hypertension with cardiovascular risk implications";
    }
    if (lower.contains("diabetes")) {
      return "diabetes with long-term metabolic risk";
    }
    if (lower.contains("heart")) {
      return "cardiac history requiring closer monitoring";
    }
    return text;
  }

  private String expandMedicationForChat(String medications) {
    String text = safeText(medications, "active long-term therapies");
    String lower = text.toLowerCase();
    if (lower.contains("lisinopril")) {
      return "lisinopril (blood-pressure management, indicates ongoing hypertension treatment)";
    }
    if (lower.contains("metformin")) {
      return "metformin (diabetes management)";
    }
    return text;
  }

  private String expandAllergyForChat(String allergies) {
    String text = safeText(allergies, "recorded allergy risks");
    String lower = text.toLowerCase();
    if (lower.contains("penicillin")) {
      return "penicillin allergy, which constrains antibiotic selection";
    }
    return text;
  }

  private String normalizeChatLower(String message) {
    String raw = message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
    return raw
        .replace('\u2019', '\'')
        .replace('\u2018', '\'')
        .replace('\u201c', '"')
        .replace('\u201d', '"');
  }

  private String chatSystemPromptBase() {
    return "You are a helpful AI copilot in the IU Indianapolis Patient AI Operations portal. "
        + "Answer both general questions and app-specific questions in clear, natural speech. "
        + "When asked about the app, explain the portal plainly. The system includes microservices, a rule-based/causal-hybrid risk engine, "
        + "counterfactual intervention analysis, and a causal guardrail that picks resilience strategies under stress. "
        + "Do not diagnose or prescribe clinical care. Keep answers concise (under 180 words). "
        + "Never show planning steps, labeled Context or Constraints blocks, or asterisk meta lists. "
        + "Do not wrap the answer in decorative quotation marks or star prefixes. "
        + "Do not use em dashes; use commas or periods instead.";
  }

  /**
   * True when the user is clearly asking for the portal's current rule-based risk summary for the
   * active patient, not when the word "risk" appears inside a medical phrase (for example metabolic risk).
   */
  private boolean isExplicitPatientRiskScoreIntent(String lower) {
    String trimmed = lower.trim().replaceAll("[?!.,]+$", "");
    if (trimmed.equals("risk") || trimmed.equals("r")) {
      return true;
    }
    if (trimmed.equals("the risk")) {
      return true;
    }
    if (lower.contains("assess risk") || lower.contains("risk assessment")) {
      return true;
    }
    if (lower.contains("current risk") || lower.contains("risk level") || lower.contains("risk score")) {
      return true;
    }
    if (lower.contains("how risky")) {
      return true;
    }
    if (lower.contains("what's the risk")
        || lower.contains("whats the risk")
        || lower.contains("what is the risk")) {
      return true;
    }
    if (lower.contains("risk for this patient") || lower.contains("risk for the patient")) {
      return true;
    }
    if (lower.contains("patient's risk") || lower.contains("patients risk")) {
      return true;
    }
    return false;
  }

  /** True for "what is X", definitions, and similar — should use Gemini, not canned DB bottleneck text. */
  private boolean isConceptOrDefinitionQuestion(String lower) {
    // Let "what is my risk / our risk" fall through to the risk assessment branch
    if (lower.contains("risk")
        && (lower.contains("my risk") || lower.contains(" our risk") || lower.contains("this patient"))) {
      return false;
    }
    if (lower.contains("what does") || lower.contains("what did ")) {
      return true;
    }
    if (lower.contains("condition") && (lower.contains("mean") || lower.contains("refer"))) {
      return true;
    }
    if (lower.contains("how to ")
        && (lower.contains("reduce") || lower.contains("lower") || lower.contains("mitigat") || lower.contains("decrease"))) {
      return true;
    }
    if (lower.contains("how can ") && (lower.contains("reduce") || lower.contains("lower") || lower.contains("risk"))) {
      return true;
    }
    if (lower.startsWith("why ") && lower.contains("risk")) {
      return true;
    }
    if (lower.contains("what is") || lower.contains("what are")) {
      return true;
    }
    if (lower.contains("who are you") || lower.contains("who is this")) {
      return true;
    }
    if (lower.contains("what's") || lower.contains("whats")) {
      return true;
    }
    if (lower.contains("define ") || lower.contains("definition of") || lower.contains("meaning of")) {
      return true;
    }
    if (lower.contains("tell me about")) {
      return true;
    }
    if (lower.contains("can you explain") || lower.contains("explain what")) {
      return true;
    }
    if ((lower.contains("how does") || lower.contains("how do"))
        && (lower.contains("work")
            || lower.contains("chaos")
            || lower.contains("guardrail")
            || lower.contains("resilience")
            || lower.contains("portal")
            || lower.contains("digital twin"))) {
      return true;
    }
    return false;
  }

  private boolean isCasualGreeting(String lower) {
    String t = lower.trim();
    if (t.length() > 36) {
      return false;
    }
    if (t.matches("^(hi|hey|h+i+)\\W*$") || t.matches("^h+i+$")) {
      return true;
    }
    return t.matches(
        "^(hello|heyy|yo|sup|hiya|thanks|thank you|thx|ok|okay|bye|good morning|good afternoon)\\b.*");
  }

  /**
   * Canned "current degradation profile" reply — only for explicit demo / simulation style prompts,
   * not for "what is a guardrail" or "what's chaos center".
   */
  private boolean wantsLiveGuardrailSimulation(String lower) {
    if (isConceptOrDefinitionQuestion(lower) || isCasualGreeting(lower)) {
      return false;
    }
    boolean topic =
        lower.contains("guardrail")
            || lower.contains("resilience")
            || lower.contains("chaos")
            || lower.contains("fault")
            || lower.contains("degradation");
    if (!topic) {
      return false;
    }
    return lower.contains("simulate")
        || lower.contains("simulation")
        || lower.contains("demo")
        || lower.contains("show me")
        || lower.contains("run ")
        || lower.contains("current ")
        || lower.contains("right now")
        || lower.contains("live ")
        || lower.contains("under stress")
        || lower.contains("under load")
        || lower.contains("mapping")
        || lower.contains("strategy for")
        || lower.contains("bottleneck")
        || (lower.contains("resilience") && lower.contains("behaviour"))
        || (lower.contains("resilience") && lower.contains("behavior"));
  }

  private String patientContextSummary(PatientRiskAssessmentRequest patient) {
    if (patient == null) {
      return "No patient context provided.";
    }
    return String.format(
        "Patient context: id=%s, name=%s, age=%s, dob=%s, address=%s, history=%s, medications=%s, allergies=%s.",
        safeText(patient.getPatientId(), "n/a"),
        safeText(patient.getName(), "n/a"),
        patient.getAge() != null ? patient.getAge() : "n/a",
        patient.getDateOfBirth(),
        safeText(patient.getAddress(), "n/a"),
        safeText(patient.getMedicalHistory(), "n/a"),
        safeText(patient.getCurrentMedications(), "n/a"),
        safeText(patient.getAllergies(), "n/a"));
  }

  private String safeText(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private String plainCause(String causeCode) {
    if (causeCode == null) return "a system issue";
    switch (causeCode) {
      case "DB_BOTTLENECK":
        return "the database is the main slowdown";
      case "AI_SERVICE_DEGRADED":
        return "the AI service is overloaded or slow";
      case "EVENT_STREAM_BACKLOG":
        return "event processing is lagging behind";
      case "HIGH_TRAFFIC":
        return "traffic is higher than normal";
      case "SERVICE_ERRORS":
        return "error rate is rising";
      default:
        return "normal operating conditions";
    }
  }

  private String plainStrategy(String strategyCode) {
    if (strategyCode == null) return "a safe fallback strategy";
    switch (strategyCode) {
      case "DB_DEGRADED_MODE":
        return "a database-protection mode with lighter queries";
      case "AI_FALLBACK_POLICY":
        return "an AI fallback policy that avoids expensive model calls";
      case "EVENTUAL_CONSISTENCY_MODE":
        return "an eventual-consistency mode to drain backlog safely";
      case "LOAD_SHEDDING_AND_CACHE":
        return "load shedding and stronger caching for stability";
      case "SAFE_MODE_DIAGNOSTIC":
        return "safe diagnostic mode until errors recover";
      default:
        return "normal operation mode";
    }
  }

  private String plainModelMode(String modelModeCode) {
    if (modelModeCode == null) return "a resilient prediction mode";
    switch (modelModeCode) {
      case "RULE_BASED_FAST":
        return "a fast rule-based model";
      case "CAUSAL_HYBRID":
        return "a causal-hybrid model with richer reasoning";
      default:
        return "the current configured model mode";
    }
  }

  private ExperimentRunResponseDTO toExperimentRunResponse(ExperimentRun run) {
    ExperimentRunResponseDTO response = new ExperimentRunResponseDTO();
    response.setId(run.getId());
    response.setModelName(run.getModelName());
    response.setScenarioName(run.getScenarioName());
    response.setAvgLatencyMs(run.getAvgLatencyMs());
    response.setAvailabilityPct(run.getAvailabilityPct());
    response.setStabilityUnderFaultPct(run.getStabilityUnderFaultPct());
    response.setExplainabilityScore(run.getExplainabilityScore());
    response.setFallbackCorrectnessPct(run.getFallbackCorrectnessPct());
    response.setNotes(run.getNotes());
    response.setCreatedAt(run.getCreatedAt());
    return response;
  }

  private static class Aggregator {
    private double latencySum;
    private double availabilitySum;
    private double stabilitySum;
    private double explainabilitySum;
    private int count;

    void accept(ExperimentRun run) {
      latencySum += run.getAvgLatencyMs();
      availabilitySum += run.getAvailabilityPct();
      stabilitySum += run.getStabilityUnderFaultPct();
      explainabilitySum += run.getExplainabilityScore();
      count++;
    }

    double avgLatencyMs() {
      return count == 0 ? 0.0 : latencySum / count;
    }

    double avgAvailabilityPct() {
      return count == 0 ? 0.0 : availabilitySum / count;
    }

    double avgStabilityUnderFaultPct() {
      return count == 0 ? 0.0 : stabilitySum / count;
    }

    double avgExplainabilityScore() {
      return count == 0 ? 0.0 : explainabilitySum / count;
    }
  }

  private PatientPredictionDTO toPredictionDTO(PatientPrediction prediction) {
    PatientPredictionDTO dto = new PatientPredictionDTO();
    dto.setId(prediction.getId());
    dto.setPatientId(prediction.getPatientId());
    dto.setPredictionType(prediction.getPredictionType());
    dto.setPredictionResult(prediction.getPredictionResult());
    dto.setConfidenceScore(prediction.getConfidenceScore());
    dto.setModelVersion(prediction.getModelVersion());
    dto.setCreatedAt(prediction.getCreatedAt());
    return dto;
  }

  @Transactional
  private void savePrediction(String patientId, String predictionType, 
      String result, Double confidence) {
    PatientPrediction prediction = new PatientPrediction();
    prediction.setPatientId(patientId);
    prediction.setPredictionType(predictionType);
    prediction.setPredictionResult(result);
    prediction.setConfidenceScore(confidence);
    prediction.setModelVersion("1.0");
    repository.save(prediction);
  }

  // Fallback method for circuit breaker
  private PatientRiskAssessmentResponse assessPatientRiskFallback(
      PatientRiskAssessmentRequest request, Exception ex) {
    log.warn("Circuit breaker fallback triggered for patient {}: {}", 
        request.getPatientId(), ex.getMessage());

    // Keep causal structure and numeric score consistent, but downgrade confidence and assessment text.
    PatientRiskAssessmentResponse response = assessRiskWithRuleBasedModel(request);
    response.setAssessment(
        "Risk assessment temporarily unavailable. Please try again later.");
    response.setConfidenceScore(0.0);
    return response;
  }
}


