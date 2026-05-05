package com.pm.patientservice.service;

import com.pm.patientservice.dto.PatientRequestDTO;
import com.pm.patientservice.dto.PatientResponseDTO;
import com.pm.patientservice.exception.DuplicatePatientProfileException;
import com.pm.patientservice.exception.EmailAlreadyExistsException;
import com.pm.patientservice.exception.PatientNotFoundException;
import com.pm.patientservice.grpc.BillingServiceGrpcClient;
import com.pm.patientservice.kafka.KafkaProducer;
import com.pm.patientservice.mapper.PatientMapper;
import com.pm.patientservice.model.Patient;
import com.pm.patientservice.repository.PatientRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PatientService {

  private static final Logger log = LoggerFactory.getLogger(PatientService.class);
  private final PatientRepository patientRepository;
  private final BillingServiceGrpcClient billingServiceGrpcClient;
  private final KafkaProducer kafkaProducer;
  private final AiIntegrationService aiIntegrationService;

  public PatientService(PatientRepository patientRepository,
      BillingServiceGrpcClient billingServiceGrpcClient,
      KafkaProducer kafkaProducer,
      AiIntegrationService aiIntegrationService) {
    this.patientRepository = patientRepository;
    this.billingServiceGrpcClient = billingServiceGrpcClient;
    this.kafkaProducer = kafkaProducer;
    this.aiIntegrationService = aiIntegrationService;
  }

  public List<PatientResponseDTO> getPatients() {
    List<Patient> patients = patientRepository.findAll();

    return patients.stream().map(PatientMapper::toDTO).toList();
  }

  public PatientResponseDTO createPatient(PatientRequestDTO patientRequestDTO) {
    if (patientRepository.existsByEmail(patientRequestDTO.getEmail())) {
      throw new EmailAlreadyExistsException(
          "A patient with this email " + "already exists"
              + patientRequestDTO.getEmail());
    }
    if (patientRepository.existsByNameIgnoreCaseAndAddressIgnoreCase(
        patientRequestDTO.getName(), patientRequestDTO.getAddress())) {
      throw new DuplicatePatientProfileException(
          "A patient with this name and address already exists.");
    }

    Patient newPatient = patientRepository.save(
        PatientMapper.toModel(patientRequestDTO));

    // Call billing service with resilience patterns (circuit breaker, retry)
    try {
      billingServiceGrpcClient.createBillingAccount(
          newPatient.getId().toString(),
          newPatient.getName(),
          newPatient.getEmail());
    } catch (Exception e) {
      // Log error but don't fail patient creation
      // In production, you might want to implement a saga pattern or outbox pattern
      log.error("Failed to create billing account for patient {}: {}", 
          newPatient.getId(), e.getMessage());
    }

    // Publish event asynchronously
    try {
      kafkaProducer.sendEvent(newPatient);
    } catch (Exception e) {
      log.error("Failed to publish patient event for patient {}: {}", 
          newPatient.getId(), e.getMessage());
      // In production, implement retry or dead letter queue
    }

    // Trigger AI risk assessment asynchronously
    try {
      aiIntegrationService.triggerRiskAssessment(newPatient);
    } catch (Exception e) {
      log.error("Failed to trigger AI risk assessment for patient {}: {}", 
          newPatient.getId(), e.getMessage());
      // Don't fail patient creation if AI service is unavailable
    }

    return PatientMapper.toDTO(newPatient);
  }

  public PatientResponseDTO updatePatient(UUID id,
      PatientRequestDTO patientRequestDTO) {

    Patient patient = patientRepository.findById(id).orElseThrow(
        () -> new PatientNotFoundException("Patient not found with ID: " + id));

    if (patientRepository.existsByEmailAndIdNot(patientRequestDTO.getEmail(),
        id)) {
      throw new EmailAlreadyExistsException(
          "A patient with this email " + "already exists"
              + patientRequestDTO.getEmail());
    }
    if (patientRepository.existsByNameIgnoreCaseAndAddressIgnoreCaseAndIdNot(
        patientRequestDTO.getName(), patientRequestDTO.getAddress(), id)) {
      throw new DuplicatePatientProfileException(
          "Another patient already uses this name and address.");
    }

    patient.setName(patientRequestDTO.getName());
    patient.setAddress(patientRequestDTO.getAddress());
    patient.setEmail(patientRequestDTO.getEmail());
    patient.setDateOfBirth(LocalDate.parse(patientRequestDTO.getDateOfBirth()));

    Patient updatedPatient = patientRepository.save(patient);
    return PatientMapper.toDTO(updatedPatient);
  }

  public void deletePatient(UUID id) {
    patientRepository.deleteById(id);
  }
}
