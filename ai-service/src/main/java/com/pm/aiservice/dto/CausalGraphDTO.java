package com.pm.aiservice.dto;

import java.util.List;

public class CausalGraphDTO {
  private List<CausalNodeDTO> nodes;
  private List<CausalEdgeDTO> edges;

  public List<CausalNodeDTO> getNodes() {
    return nodes;
  }

  public void setNodes(List<CausalNodeDTO> nodes) {
    this.nodes = nodes;
  }

  public List<CausalEdgeDTO> getEdges() {
    return edges;
  }

  public void setEdges(List<CausalEdgeDTO> edges) {
    this.edges = edges;
  }
}

