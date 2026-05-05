package com.pm.patientservice.repository;

import com.pm.patientservice.model.Patient;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {
  boolean existsByEmail(String email);

  boolean existsByEmailAndIdNot(String email, UUID id);

  boolean existsByNameIgnoreCaseAndAddressIgnoreCase(String name, String address);

  boolean existsByNameIgnoreCaseAndAddressIgnoreCaseAndIdNot(
      String name, String address, UUID id);
}
