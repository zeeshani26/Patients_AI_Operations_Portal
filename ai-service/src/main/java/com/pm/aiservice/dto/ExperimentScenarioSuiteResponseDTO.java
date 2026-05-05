package com.pm.aiservice.dto;

import java.util.List;
import java.util.Map;

public class ExperimentScenarioSuiteResponseDTO {
  private int executedRuns;
  private List<ExperimentRunResponseDTO> loggedRuns;
  private Map<String, String> guardrailStrategiesByScenario;

  public int getExecutedRuns() {
    return executedRuns;
  }

  public void setExecutedRuns(int executedRuns) {
    this.executedRuns = executedRuns;
  }

  public List<ExperimentRunResponseDTO> getLoggedRuns() {
    return loggedRuns;
  }

  public void setLoggedRuns(List<ExperimentRunResponseDTO> loggedRuns) {
    this.loggedRuns = loggedRuns;
  }

  public Map<String, String> getGuardrailStrategiesByScenario() {
    return guardrailStrategiesByScenario;
  }

  public void setGuardrailStrategiesByScenario(Map<String, String> guardrailStrategiesByScenario) {
    this.guardrailStrategiesByScenario = guardrailStrategiesByScenario;
  }
}
