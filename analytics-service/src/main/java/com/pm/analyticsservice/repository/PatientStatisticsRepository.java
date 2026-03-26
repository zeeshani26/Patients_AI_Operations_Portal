package com.pm.analyticsservice.repository;

import com.pm.analyticsservice.model.PatientStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PatientStatisticsRepository extends JpaRepository<PatientStatistics, String> {
}


