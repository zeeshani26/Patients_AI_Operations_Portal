package com.pm.analyticsservice.service;

import com.pm.analyticsservice.model.PatientEventAnalytics;
import com.pm.analyticsservice.model.PatientStatistics;
import com.pm.analyticsservice.repository.PatientEventAnalyticsRepository;
import com.pm.analyticsservice.repository.PatientStatisticsRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {

  private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);
  private final PatientEventAnalyticsRepository eventRepository;
  private final PatientStatisticsRepository statisticsRepository;

  public AnalyticsService(
      PatientEventAnalyticsRepository eventRepository,
      PatientStatisticsRepository statisticsRepository) {
    this.eventRepository = eventRepository;
    this.statisticsRepository = statisticsRepository;
  }

  @Transactional
  public void processPatientEvent(String patientId, String name, String email, String eventType) {
    log.info("Processing patient event: {} for patient {}", eventType, patientId);

    // Save event for analytics
    PatientEventAnalytics event = new PatientEventAnalytics();
    event.setPatientId(patientId);
    event.setPatientName(name);
    event.setPatientEmail(email);
    event.setEventType(eventType);
    eventRepository.save(event);

    // Update statistics if it's a creation event
    if ("PATIENT_CREATED".equals(eventType)) {
      updateStatistics();
    }

    log.info("Successfully processed patient event: {} for patient {}", eventType, patientId);
  }

  @Transactional
  private void updateStatistics() {
    PatientStatistics stats = statisticsRepository.findById("GLOBAL_STATS")
        .orElseGet(() -> {
          PatientStatistics newStats = new PatientStatistics();
          statisticsRepository.save(newStats);
          return newStats;
        });

    LocalDateTime now = LocalDateTime.now();
    LocalDateTime startOfDay = now.truncatedTo(ChronoUnit.DAYS);
    LocalDateTime startOfWeek = now.minusDays(now.getDayOfWeek().getValue() - 1).truncatedTo(ChronoUnit.DAYS);
    LocalDateTime startOfMonth = now.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);

    stats.setTotalPatients(eventRepository.countTotalPatientCreations());
    stats.setPatientsCreatedToday(
        eventRepository.countByEventTypeSince("PATIENT_CREATED", startOfDay));
    stats.setPatientsCreatedThisWeek(
        eventRepository.countByEventTypeSince("PATIENT_CREATED", startOfWeek));
    stats.setPatientsCreatedThisMonth(
        eventRepository.countByEventTypeSince("PATIENT_CREATED", startOfMonth));

    statisticsRepository.save(stats);
    log.debug("Updated patient statistics: total={}, today={}, week={}, month={}",
        stats.getTotalPatients(), stats.getPatientsCreatedToday(),
        stats.getPatientsCreatedThisWeek(), stats.getPatientsCreatedThisMonth());
  }

  public PatientStatistics getStatistics() {
    return statisticsRepository.findById("GLOBAL_STATS")
        .orElseGet(PatientStatistics::new);
  }
}


