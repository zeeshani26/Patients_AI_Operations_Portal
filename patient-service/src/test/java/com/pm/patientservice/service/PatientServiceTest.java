package com.pm.patientservice.service;

import com.pm.patientservice.dto.PatientRequestDTO;
import com.pm.patientservice.dto.PatientResponseDTO;
import com.pm.patientservice.exception.EmailAlreadyExistsException;
import com.pm.patientservice.exception.PatientNotFoundException;
import com.pm.patientservice.grpc.BillingServiceGrpcClient;
import com.pm.patientservice.kafka.KafkaProducer;
import com.pm.patientservice.mapper.PatientMapper;
import com.pm.patientservice.model.Patient;
import com.pm.patientservice.repository.PatientRepository;
import billing.BillingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

  @Mock
  private PatientRepository patientRepository;

  @Mock
  private BillingServiceGrpcClient billingServiceGrpcClient;

  @Mock
  private KafkaProducer kafkaProducer;

  @Mock
  private AiIntegrationService aiIntegrationService;

  @InjectMocks
  private PatientService patientService;

  private Patient testPatient;
  private PatientRequestDTO testPatientRequestDTO;

  @BeforeEach
  void setUp() {
    testPatient = new Patient();
    testPatient.setId(UUID.randomUUID());
    testPatient.setName("John Doe");
    testPatient.setEmail("john.doe@example.com");
    testPatient.setAddress("123 Main St");
    testPatient.setDateOfBirth(LocalDate.of(1990, 1, 1));
    testPatient.setRegisteredDate(LocalDate.now());

    testPatientRequestDTO = new PatientRequestDTO();
    testPatientRequestDTO.setName("John Doe");
    testPatientRequestDTO.setEmail("john.doe@example.com");
    testPatientRequestDTO.setAddress("123 Main St");
    testPatientRequestDTO.setDateOfBirth("1990-01-01");
    testPatientRequestDTO.setRegisteredDate("2024-01-01");
  }

  @Test
  void getPatients_ShouldReturnListOfPatients() {
    // Given
    when(patientRepository.findAll()).thenReturn(List.of(testPatient));

    // When
    List<PatientResponseDTO> result = patientService.getPatients();

    // Then
    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals(testPatient.getName(), result.get(0).getName());
    verify(patientRepository).findAll();
  }

  @Test
  void createPatient_ShouldCreatePatientSuccessfully() {
    // Given
    when(patientRepository.existsByEmail(testPatientRequestDTO.getEmail())).thenReturn(false);
    when(patientRepository.save(any(Patient.class))).thenReturn(testPatient);
    when(billingServiceGrpcClient.createBillingAccount(anyString(), anyString(), anyString()))
        .thenReturn(BillingResponse.newBuilder()
            .setAccountId("BILL-123")
            .setStatus("ACTIVE")
            .build());

    // When
    PatientResponseDTO result = patientService.createPatient(testPatientRequestDTO);

    // Then
    assertNotNull(result);
    assertEquals(testPatient.getName(), result.getName());
    verify(patientRepository).save(any(Patient.class));
    verify(billingServiceGrpcClient).createBillingAccount(anyString(), anyString(), anyString());
    verify(kafkaProducer).sendEvent(any(Patient.class));
  }

  @Test
  void createPatient_ShouldThrowExceptionWhenEmailExists() {
    // Given
    when(patientRepository.existsByEmail(testPatientRequestDTO.getEmail())).thenReturn(true);

    // When & Then
    assertThrows(EmailAlreadyExistsException.class, 
        () -> patientService.createPatient(testPatientRequestDTO));
    verify(patientRepository, never()).save(any(Patient.class));
  }

  @Test
  void updatePatient_ShouldUpdatePatientSuccessfully() {
    // Given
    UUID patientId = testPatient.getId();
    when(patientRepository.findById(patientId)).thenReturn(Optional.of(testPatient));
    when(patientRepository.existsByEmailAndIdNot(testPatientRequestDTO.getEmail(), patientId))
        .thenReturn(false);
    when(patientRepository.save(any(Patient.class))).thenReturn(testPatient);

    // When
    PatientResponseDTO result = patientService.updatePatient(patientId, testPatientRequestDTO);

    // Then
    assertNotNull(result);
    verify(patientRepository).findById(patientId);
    verify(patientRepository).save(any(Patient.class));
  }

  @Test
  void updatePatient_ShouldThrowExceptionWhenPatientNotFound() {
    // Given
    UUID patientId = UUID.randomUUID();
    when(patientRepository.findById(patientId)).thenReturn(Optional.empty());

    // When & Then
    assertThrows(PatientNotFoundException.class, 
        () -> patientService.updatePatient(patientId, testPatientRequestDTO));
    verify(patientRepository, never()).save(any(Patient.class));
  }

  @Test
  void deletePatient_ShouldDeletePatientSuccessfully() {
    // Given
    UUID patientId = testPatient.getId();

    // When
    patientService.deletePatient(patientId);

    // Then
    verify(patientRepository).deleteById(patientId);
  }

  @Test
  void createPatient_ShouldHandleBillingServiceFailureGracefully() {
    // Given
    when(patientRepository.existsByEmail(testPatientRequestDTO.getEmail())).thenReturn(false);
    when(patientRepository.save(any(Patient.class))).thenReturn(testPatient);
    when(billingServiceGrpcClient.createBillingAccount(anyString(), anyString(), anyString()))
        .thenThrow(new RuntimeException("Billing service unavailable"));

    // When
    PatientResponseDTO result = patientService.createPatient(testPatientRequestDTO);

    // Then
    assertNotNull(result);
    // Patient should still be created even if billing service fails
    verify(patientRepository).save(any(Patient.class));
  }
}


