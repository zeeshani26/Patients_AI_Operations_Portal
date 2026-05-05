package com.pm.aiservice.dto;

public class ChatMessageResponseDTO {
  private String answer;
  private String actionHint;
  private String confidence;

  public String getAnswer() {
    return answer;
  }

  public void setAnswer(String answer) {
    this.answer = answer;
  }

  public String getActionHint() {
    return actionHint;
  }

  public void setActionHint(String actionHint) {
    this.actionHint = actionHint;
  }

  public String getConfidence() {
    return confidence;
  }

  public void setConfidence(String confidence) {
    this.confidence = confidence;
  }
}
