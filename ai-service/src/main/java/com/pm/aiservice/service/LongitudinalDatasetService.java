package com.pm.aiservice.service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LongitudinalDatasetService {
  private static final Logger log = LoggerFactory.getLogger(LongitudinalDatasetService.class);

  private final String datasetPath;
  private final Map<String, CohortStats> conditionStats = new HashMap<>();
  private CohortStats globalStats = new CohortStats();
  private boolean loaded;

  public LongitudinalDatasetService(
      @Value("${ai.dataset.longitudinal.path:../healthcare_dataset_longitudinal.csv}") String datasetPath) {
    this.datasetPath = datasetPath;
  }

  @PostConstruct
  public void init() {
    loadDataset();
  }

  public boolean isLoaded() {
    return loaded;
  }

  public CohortStats getConditionStats(String condition) {
    if (condition == null || condition.isBlank()) {
      return globalStats;
    }
    CohortStats stats = conditionStats.get(condition.trim().toLowerCase(Locale.ROOT));
    return stats == null ? globalStats : stats;
  }

  private void loadDataset() {
    Path path = Paths.get(datasetPath).normalize();
    if (!Files.exists(path)) {
      log.warn("Longitudinal dataset not found at {}", path.toAbsolutePath());
      loaded = false;
      return;
    }

    try (BufferedReader reader = Files.newBufferedReader(path)) {
      String headerLine = reader.readLine();
      if (headerLine == null) {
        loaded = false;
        return;
      }
      String[] headers = headerLine.split(",", -1);
      Map<String, Integer> idx = index(headers);
      int condIdx = idx.getOrDefault("Medical Condition", -1);
      int readmitIdx = idx.getOrDefault("outcome_readmission_30d", -1);
      int complicationIdx = idx.getOrDefault("outcome_complication_90d", -1);
      int mortalityIdx = idx.getOrDefault("outcome_mortality_1y", -1);
      int severityIdx = idx.getOrDefault("severity_score", -1);
      int treatmentIntensityIdx = idx.getOrDefault("treatment_intensity_score", -1);

      if (condIdx < 0 || readmitIdx < 0 || complicationIdx < 0 || mortalityIdx < 0) {
        log.warn("Dataset missing required columns for causal calibration.");
        loaded = false;
        return;
      }

      String line;
      while ((line = reader.readLine()) != null) {
        String[] values = line.split(",", -1);
        String condition = safeValue(values, condIdx).trim().toLowerCase(Locale.ROOT);
        double readmit = parseDoubleSafe(safeValue(values, readmitIdx));
        double complication = parseDoubleSafe(safeValue(values, complicationIdx));
        double mortality = parseDoubleSafe(safeValue(values, mortalityIdx));
        double severity = parseDoubleSafe(safeValue(values, severityIdx));
        double treatmentIntensity = parseDoubleSafe(safeValue(values, treatmentIntensityIdx));

        CohortStats stats = conditionStats.computeIfAbsent(condition, key -> new CohortStats());
        stats.accept(readmit, complication, mortality, severity, treatmentIntensity);
        globalStats.accept(readmit, complication, mortality, severity, treatmentIntensity);
      }

      globalStats.finalizeStats();
      for (CohortStats stats : conditionStats.values()) {
        stats.finalizeStats();
      }
      loaded = true;
      log.info("Loaded longitudinal dataset {} with {} condition cohorts",
          path.toAbsolutePath(), conditionStats.size());
    } catch (IOException e) {
      loaded = false;
      log.warn("Failed to load longitudinal dataset: {}", e.getMessage());
    }
  }

  private static Map<String, Integer> index(String[] headers) {
    Map<String, Integer> idx = new HashMap<>();
    for (int i = 0; i < headers.length; i++) {
      idx.put(headers[i].trim(), i);
    }
    return idx;
  }

  private static String safeValue(String[] values, int index) {
    if (index < 0 || index >= values.length) {
      return "";
    }
    return values[index];
  }

  private static double parseDoubleSafe(String value) {
    try {
      return Double.parseDouble(value.trim());
    } catch (Exception e) {
      return 0.0;
    }
  }

  public static class CohortStats {
    private double avgOutcomeRisk;
    private double avgSeverity;
    private double avgTreatmentIntensity;
    private int count;

    private double outcomeAccumulator;
    private double severityAccumulator;
    private double treatmentAccumulator;

    void accept(double readmit, double complication, double mortality, double severity,
                double treatmentIntensity) {
      double outcome = (readmit + complication + mortality) / 3.0;
      outcomeAccumulator += outcome;
      severityAccumulator += severity;
      treatmentAccumulator += treatmentIntensity;
      count++;
    }

    void finalizeStats() {
      if (count == 0) {
        avgOutcomeRisk = 0.0;
        avgSeverity = 0.0;
        avgTreatmentIntensity = 0.0;
        return;
      }
      avgOutcomeRisk = outcomeAccumulator / count;
      avgSeverity = severityAccumulator / count;
      avgTreatmentIntensity = treatmentAccumulator / count;
    }

    public double getAvgOutcomeRisk() {
      return avgOutcomeRisk;
    }

    public double getAvgSeverity() {
      return avgSeverity;
    }

    public double getAvgTreatmentIntensity() {
      return avgTreatmentIntensity;
    }

    public int getCount() {
      return count;
    }
  }
}
