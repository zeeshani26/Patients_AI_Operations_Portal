package com.pm.analyticsservice.service;

import com.pm.analyticsservice.model.PatientStatistics;
import com.pm.analyticsservice.repository.PatientEventAnalyticsRepository;
import com.pm.analyticsservice.repository.PatientStatisticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

  @Mock
  private PatientEventAnalyticsRepository eventRepository;

  @Mock
  private PatientStatisticsRepository statisticsRepository;

  @InjectMocks
  private AnalyticsService service;

  @Test
  void processPatientEvent_ShouldSaveEvent() {
    // Given
    when(eventRepository.countTotalPatientCreations()).thenReturn(10L);
    when(eventRepository.countByEventTypeSince(anyString(), any())).thenReturn(5L);
    when(statisticsRepository.findById("GLOBAL_STATS")).thenReturn(Optional.empty());
    when(statisticsRepository.save(any(PatientStatistics.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    service.processPatientEvent("patient-123", "John Doe", "john@example.com", "PATIENT_CREATED");

    // Then
    verify(eventRepository).save(any());
    // First save creates GLOBAL_STATS in orElseGet; second persists updated aggregates
    verify(statisticsRepository, times(2)).save(any(PatientStatistics.class));
  }

  @Test
  void getStatistics_ShouldReturnStatistics() {
    // Given
    PatientStatistics stats = new PatientStatistics();
    stats.setTotalPatients(100L);
    when(statisticsRepository.findById("GLOBAL_STATS")).thenReturn(Optional.of(stats));

    // When
    PatientStatistics result = service.getStatistics();

    // Then
    assertNotNull(result);
    assertEquals(100L, result.getTotalPatients());
  }

  @Test
  void getStatistics_ShouldReturnNewStatisticsWhenNotFound() {
    // Given
    when(statisticsRepository.findById("GLOBAL_STATS")).thenReturn(Optional.empty());

    // When
    PatientStatistics result = service.getStatistics();

    // Then
    assertNotNull(result);
    assertEquals(0L, result.getTotalPatients());
  }
}


