package com.pm.analyticsservice.kafka;

import com.google.protobuf.InvalidProtocolBufferException;
import com.pm.analyticsservice.service.AnalyticsService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import patient.events.PatientEvent;

@Service
public class KafkaConsumer {

  private static final Logger log = LoggerFactory.getLogger(
      KafkaConsumer.class);

  private final AnalyticsService analyticsService;
  private final Counter eventProcessedCounter;
  private final Counter eventErrorCounter;

  public KafkaConsumer(
      AnalyticsService analyticsService,
      MeterRegistry meterRegistry) {
    this.analyticsService = analyticsService;
    this.eventProcessedCounter = Counter.builder("analytics.events.processed")
        .description("Number of patient events processed")
        .register(meterRegistry);
    this.eventErrorCounter = Counter.builder("analytics.events.errors")
        .description("Number of errors processing patient events")
        .register(meterRegistry);
  }

  @KafkaListener(topics = "patient", groupId = "analytics-service")
  public void consumeEvent(
      @Payload byte[] event,
      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
      Acknowledgment acknowledgment) {
    
    try {
      PatientEvent patientEvent = PatientEvent.parseFrom(event);
      
      log.info("Received Patient Event: [PatientId={}, PatientName={}, PatientEmail={}, EventType={}]",
          patientEvent.getPatientId(),
          patientEvent.getName(),
          patientEvent.getEmail(),
          patientEvent.getEventType());

      // Process event with business logic
      analyticsService.processPatientEvent(
          patientEvent.getPatientId(),
          patientEvent.getName(),
          patientEvent.getEmail(),
          patientEvent.getEventType()
      );

      eventProcessedCounter.increment();
      
      // Manual acknowledgment for reliability
      if (acknowledgment != null) {
        acknowledgment.acknowledge();
      }

      log.debug("Successfully processed patient event for patient: {}", patientEvent.getPatientId());

    } catch (InvalidProtocolBufferException e) {
      eventErrorCounter.increment();
      log.error("Error deserializing event from topic {}: {}", topic, e.getMessage(), e);
      // In production, you might want to send to a dead letter queue
      if (acknowledgment != null) {
        acknowledgment.acknowledge(); // Acknowledge to prevent infinite retries of bad messages
      }
    } catch (Exception e) {
      eventErrorCounter.increment();
      log.error("Error processing patient event from topic {}: {}", topic, e.getMessage(), e);
      // In production, implement retry logic or dead letter queue
      if (acknowledgment != null) {
        acknowledgment.acknowledge(); // Acknowledge to prevent infinite retries
      }
    }
  }
}
