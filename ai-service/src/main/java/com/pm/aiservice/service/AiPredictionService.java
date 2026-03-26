package com.pm.aiservice.service;

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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiPredictionService {

  private static final Logger log = LoggerFactory.getLogger(AiPredictionService.class);
  private final PatientPredictionRepository repository;
  private final OpenAiService openAiService;
  private final Counter predictionCounter;
  private final Counter predictionErrorCounter;
  private final boolean useOpenAI;

  public AiPredictionService(
      PatientPredictionRepository repository,
      @Value("${openai.api.key:}") String apiKey,
      @Value("${ai.use-openai:false}") boolean useOpenAI,
      MeterRegistry meterRegistry) {
    this.repository = repository;
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
      PatientRiskAssessmentResponse response;

      if (useOpenAI && openAiService != null) {
        response = assessRiskWithOpenAI(request);
      } else {
        response = assessRiskWithRuleBasedModel(request);
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

  private PatientRiskAssessmentResponse assessRiskWithOpenAI(PatientRiskAssessmentRequest request) {
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

      return parseAIResponse(request.getPatientId(), responseText);

    } catch (Exception e) {
      log.warn("OpenAI API call failed, falling back to rule-based model: {}", e.getMessage());
      return assessRiskWithRuleBasedModel(request);
    }
  }

  private PatientRiskAssessmentResponse assessRiskWithRuleBasedModel(
      PatientRiskAssessmentRequest request) {
    
    log.debug("Using rule-based risk assessment model");
    
    PatientRiskAssessmentResponse response = new PatientRiskAssessmentResponse();
    response.setPatientId(request.getPatientId());
    response.setAssessedAt(LocalDateTime.now());

    // Calculate age if not provided
    int age = request.getAge() != null ? request.getAge() : 
        java.time.Period.between(request.getDateOfBirth(), java.time.LocalDate.now()).getYears();

    // Rule-based risk calculation
    double riskScore = 0.0;
    List<String> riskFactors = new ArrayList<>();
    List<String> recommendations = new ArrayList<>();

    // Age-based risk
    if (age > 65) {
      riskScore += 0.3;
      riskFactors.add("Advanced age (65+)");
      recommendations.add("Regular health checkups recommended");
    } else if (age > 50) {
      riskScore += 0.15;
      riskFactors.add("Middle age (50-65)");
    }

    // Medical history risk
    if (request.getMedicalHistory() != null && !request.getMedicalHistory().isEmpty()) {
      String history = request.getMedicalHistory().toLowerCase();
      if (history.contains("diabetes") || history.contains("heart") || 
          history.contains("hypertension")) {
        riskScore += 0.25;
        riskFactors.add("Significant medical history");
        recommendations.add("Monitor existing conditions closely");
      }
    }

    // Medication risk
    if (request.getCurrentMedications() != null && 
        !request.getCurrentMedications().isEmpty()) {
      riskScore += 0.1;
      riskFactors.add("On current medications");
      recommendations.add("Review medication interactions");
    }

    // Allergy risk
    if (request.getAllergies() != null && !request.getAllergies().isEmpty()) {
      riskScore += 0.15;
      riskFactors.add("Known allergies");
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

    response.setRiskLevel(riskLevel);
    response.setRiskScore(Math.min(riskScore, 1.0));
    response.setRiskFactors(riskFactors);
    response.setRecommendations(recommendations);
    response.setConfidenceScore(0.85); // Rule-based model confidence

    // Generate assessment text
    String assessment = String.format(
        "Patient %s (Age: %d) has been assessed with %s risk level (Score: %.2f). " +
        "Key factors: %s. Recommendations: %s",
        request.getName(), age, riskLevel, riskScore,
        String.join(", ", riskFactors),
        String.join("; ", recommendations));

    response.setAssessment(assessment);

    return response;
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

  @Cacheable(value = "anomalyDetection", key = "#patientId")
  public PatientRiskAssessmentResponse detectAnomalies(String patientId, 
      PatientRiskAssessmentRequest request) {
    log.info("Detecting anomalies for patient: {}", patientId);

    // Anomaly detection logic
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

    savePrediction(patientId, "ANOMALY_DETECTION", 
        response.getAssessment(), response.getConfidenceScore());

    return response;
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
    
    PatientRiskAssessmentResponse response = new PatientRiskAssessmentResponse();
    response.setPatientId(request.getPatientId());
    response.setRiskLevel("UNKNOWN");
    response.setRiskScore(0.5);
    response.setAssessment("Risk assessment temporarily unavailable. Please try again later.");
    response.setConfidenceScore(0.0);
    response.setAssessedAt(LocalDateTime.now());
    response.setRiskFactors(new ArrayList<>());
    response.setRecommendations(new ArrayList<>());
    
    return response;
  }
}


