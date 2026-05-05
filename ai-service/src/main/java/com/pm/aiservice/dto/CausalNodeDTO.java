package com.pm.aiservice.dto;

public class CausalNodeDTO {
  private String id;
  private String label;
  private String type; // e.g. FACTOR, RISK_SCORE

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }
}

