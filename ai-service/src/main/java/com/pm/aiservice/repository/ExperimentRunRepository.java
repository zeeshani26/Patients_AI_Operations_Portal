package com.pm.aiservice.repository;

import com.pm.aiservice.model.ExperimentRun;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExperimentRunRepository extends JpaRepository<ExperimentRun, UUID> {
  List<ExperimentRun> findTop200ByOrderByCreatedAtDesc();
}
