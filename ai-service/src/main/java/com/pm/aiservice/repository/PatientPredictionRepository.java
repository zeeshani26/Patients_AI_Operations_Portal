package com.pm.aiservice.repository;

import com.pm.aiservice.model.PatientPrediction;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PatientPredictionRepository extends JpaRepository<PatientPrediction, UUID> {
  List<PatientPrediction> findByPatientId(String patientId);
  List<PatientPrediction> findByPredictionType(String predictionType);
  List<PatientPrediction> findByPatientIdAndPredictionType(String patientId, String predictionType);
}


