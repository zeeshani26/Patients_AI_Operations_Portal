package com.pm.patientservice.kafka;

import com.pm.patientservice.model.Patient;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import patient.events.PatientEvent;

@Service
public class KafkaProducer {

  private static final Logger log = LoggerFactory.getLogger(
      KafkaProducer.class);
  private final KafkaTemplate<String, byte[]> kafkaTemplate;
  private final Counter eventSentCounter;
  private final Counter eventErrorCounter;

  public KafkaProducer(
      KafkaTemplate<String, byte[]> kafkaTemplate,
      MeterRegistry meterRegistry) {
    this.kafkaTemplate = kafkaTemplate;
    this.eventSentCounter = Counter.builder("patient.events.sent")
        .description("Number of patient events sent to Kafka")
        .register(meterRegistry);
    this.eventErrorCounter = Counter.builder("patient.events.errors")
        .description("Number of errors sending patient events")
        .register(meterRegistry);
  }

  public void sendEvent(Patient patient) {
    PatientEvent event = PatientEvent.newBuilder()
        .setPatientId(patient.getId().toString())
        .setName(patient.getName())
        .setEmail(patient.getEmail())
        .setEventType("PATIENT_CREATED")
        .build();

    try {
      CompletableFuture<SendResult<String, byte[]>> future =
          kafkaTemplate.send("patient", patient.getId().toString(), event.toByteArray());
      future.whenComplete((result, ex) -> {
        if (ex != null) {
          log.error("Error sending PatientCreated event for patient {}: {}",
              patient.getId(), ex.getMessage(), ex);
          eventErrorCounter.increment();
        } else {
          log.debug("Successfully sent patient event for patient: {} to topic: {}",
              patient.getId(), result.getRecordMetadata().topic());
          eventSentCounter.increment();
        }
      });
    } catch (Exception e) {
      log.error("Error sending PatientCreated event for patient {}: {}",
          patient.getId(), e.getMessage(), e);
      eventErrorCounter.increment();
    }
  }
}
