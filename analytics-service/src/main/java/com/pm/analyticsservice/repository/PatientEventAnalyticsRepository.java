package com.pm.analyticsservice.repository;

import com.pm.analyticsservice.model.PatientEventAnalytics;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PatientEventAnalyticsRepository extends JpaRepository<PatientEventAnalytics, java.util.UUID> {
  
  List<PatientEventAnalytics> findByEventType(String eventType);
  
  @Query("SELECT COUNT(e) FROM PatientEventAnalytics e WHERE e.eventType = :eventType AND e.createdAt >= :startDate")
  Long countByEventTypeSince(@Param("eventType") String eventType, @Param("startDate") LocalDateTime startDate);
  
  @Query("SELECT COUNT(e) FROM PatientEventAnalytics e WHERE e.eventType = 'PATIENT_CREATED'")
  Long countTotalPatientCreations();
}


