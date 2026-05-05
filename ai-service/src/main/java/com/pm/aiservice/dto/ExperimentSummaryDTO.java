package com.pm.aiservice.dto;

import java.util.List;

public class ExperimentSummaryDTO {
  private int totalRuns;
  private List<ModelComparisonDTO> aggregatedByModel;
  private List<ExperimentRunResponseDTO> recentRuns;

  public int getTotalRuns() {
    return totalRuns;
  }

  public void setTotalRuns(int totalRuns) {
    this.totalRuns = totalRuns;
  }

  public List<ModelComparisonDTO> getAggregatedByModel() {
    return aggregatedByModel;
  }

  public void setAggregatedByModel(List<ModelComparisonDTO> aggregatedByModel) {
    this.aggregatedByModel = aggregatedByModel;
  }

  public List<ExperimentRunResponseDTO> getRecentRuns() {
    return recentRuns;
  }

  public void setRecentRuns(List<ExperimentRunResponseDTO> recentRuns) {
    this.recentRuns = recentRuns;
  }
}
