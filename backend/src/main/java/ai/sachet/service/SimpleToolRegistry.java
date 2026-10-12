package ai.sachet.service;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/** Simple in-memory implementation of ToolRegistry. */
@Service
public class SimpleToolRegistry implements ToolRegistry {

  private final Map<String, ToolDefinition> toolMap;

  public SimpleToolRegistry() {
    this.toolMap = new HashMap<>();
  }

  @Override
  public void registerTool(ToolDefinition toolDefinition) {
    toolMap.put(toolDefinition.getName(), toolDefinition);
  }

  @Override
  public Map<String, ToolDefinition> getAllTools() {
    return Map.copyOf(toolMap);
  }

  @Override
  public ToolDefinition getTool(String toolName) {
    ToolDefinition tool = toolMap.get(toolName);
    if (tool == null) {
      throw new IllegalArgumentException("Tool not registered: " + toolName);
    }
    return tool;
  }

  @Override
  public List<ToolDefinition> getToolsByRiskLevel(ToolRiskLevel riskLevel) {
    return toolMap.values().stream()
        .filter(t -> t.getRiskLevel() == riskLevel)
        .collect(Collectors.toList());
  }

  @Override
  public boolean isToolRegistered(String toolName) {
    return toolMap.containsKey(toolName);
  }

  @Override
  public List<String> getToolNames() {
    return new ArrayList<>(toolMap.keySet());
  }
}