package com.pm.analyticsservice.controller;

import com.pm.analyticsservice.model.PatientStatistics;
import com.pm.analyticsservice.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analytics")
@Tag(name = "Analytics", description = "API for patient analytics and statistics")
public class AnalyticsController {

  private final AnalyticsService analyticsService;

  public AnalyticsController(AnalyticsService analyticsService) {
    this.analyticsService = analyticsService;
  }

  @GetMapping("/statistics")
  @Operation(summary = "Get patient statistics")
  public ResponseEntity<PatientStatistics> getStatistics() {
    PatientStatistics statistics = analyticsService.getStatistics();
    return ResponseEntity.ok(statistics);
  }
}


