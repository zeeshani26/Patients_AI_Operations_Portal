package com.pm.patientservice.exception;

/** Raised when another patient already has the same name and address (case-insensitive). */
public class DuplicatePatientProfileException extends RuntimeException {

  public DuplicatePatientProfileException(String message) {
    super(message);
  }
}
