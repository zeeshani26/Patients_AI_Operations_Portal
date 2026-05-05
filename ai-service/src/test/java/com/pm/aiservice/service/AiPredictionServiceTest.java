package com.pm.aiservice.service;

import com.pm.aiservice.dto.ChatMessageRequestDTO;
import com.pm.aiservice.dto.ChatMessageResponseDTO;
import com.pm.aiservice.dto.PatientRiskAssessmentRequest;
import com.pm.aiservice.dto.PatientRiskAssessmentResponse;
import com.pm.aiservice.repository.ExperimentRunRepository;
import com.pm.aiservice.repository.PatientPredictionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiPredictionServiceTest {

  @Mock
  private PatientPredictionRepository repository;

  @Mock
  private ExperimentRunRepository experimentRunRepository;

  @Mock
  private LongitudinalDatasetService datasetService;

  @Mock
  private GeminiChatService geminiChatService;

  @InjectMocks
  private AiPredictionService service;

  private PatientRiskAssessmentRequest request;

  @BeforeEach
  void setUp() {
    // Create service with rule-based model (no OpenAI)
    service = new AiPredictionService(
        repository,
        experimentRunRepository,
        datasetService,
        geminiChatService,
        "../model-comparison-evidence/ml_model_metrics.json",
        "",
        false,
        new SimpleMeterRegistry()
    );

    request = new PatientRiskAssessmentRequest();
    request.setPatientId("patient-123");
    request.setName("John Doe");
    request.setEmail("john@example.com");
    request.setDateOfBirth(LocalDate.of(1990, 1, 1));
    request.setAge(34);
  }

  @Test
  void assessPatientRisk_ShouldReturnResponse() {
    // When
    PatientRiskAssessmentResponse response = service.assessPatientRisk(request);

    // Then
    assertNotNull(response);
    assertEquals("patient-123", response.getPatientId());
    assertNotNull(response.getRiskLevel());
    assertNotNull(response.getRiskScore());
    assertNotNull(response.getAssessment());
    verify(repository).save(any());
  }

  @Test
  void assessPatientRisk_ShouldCalculateAgeCorrectly() {
    // Given
    request.setAge(null);
    request.setDateOfBirth(LocalDate.now().minusYears(70));

    // When
    PatientRiskAssessmentResponse response = service.assessPatientRisk(request);

    // Then
    assertNotNull(response);
    // Should have higher risk due to age
    assertTrue(response.getRiskScore() > 0.2);
  }

  @Test
  void detectAnomalies_ShouldDetectAgeMismatch() {
    // Given
    request.setAge(30);
    request.setDateOfBirth(LocalDate.now().minusYears(50)); // Mismatch

    // When
    PatientRiskAssessmentResponse response = service.detectAnomalies(
        request.getPatientId(), request);

    // Then
    assertNotNull(response);
    assertTrue(response.getRiskScore() > 0.0);
  }

  @Test
  void detectAnomalies_ShouldDetectInvalidEmail() {
    // Given
    request.setEmail("invalid-email"); // No @ symbol

    // When
    PatientRiskAssessmentResponse response = service.detectAnomalies(
        request.getPatientId(), request);

    // Then
    assertNotNull(response);
    assertTrue(response.getRiskScore() > 0.0);
  }

  @Test
  void chatWithAssistant_conditionDefinitionWithMetabolicRisk_usesGeminiNotRuleSummary() {
    when(geminiChatService.isConfigured()).thenReturn(true);
    when(geminiChatService.generateReply(anyString(), anyString()))
        .thenReturn(Optional.of("Metabolic risk in diabetes refers to long-run blood sugar and circulation stress."));

    ChatMessageRequestDTO req = new ChatMessageRequestDTO();
    req.setMessage("what does this condition mean: diabetes with long-term metabolic risk");
    req.setPatientContext(request);

    ChatMessageResponseDTO res = service.chatWithAssistant(req);

    assertFalse(res.getAnswer().contains("For patient"));
    assertTrue(res.getAnswer().toLowerCase().contains("diabetes"));
    verify(geminiChatService).generateReply(anyString(), contains("diabetes"));
  }

  @Test
  void chatWithAssistant_oneWordRisk_returnsStructuredPortalRisk() {
    when(geminiChatService.isConfigured()).thenReturn(false);

    ChatMessageRequestDTO req = new ChatMessageRequestDTO();
    req.setMessage("risk");
    req.setPatientContext(request);

    ChatMessageResponseDTO res = service.chatWithAssistant(req);
    assertTrue(res.getAnswer().contains("For patient"));
    assertTrue(res.getAnswer().contains("John Doe"));
  }
}


