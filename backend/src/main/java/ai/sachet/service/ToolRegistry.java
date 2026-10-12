package ai.sachet.service;

import java.util.List;
import java.util.Map;

/** Registry for explicitly registered tools/operations. */
public interface ToolRegistry {

  /** Register a new tool with its risk profile and permissions. */
  void registerTool(ToolDefinition toolDefinition);

  /** Get all registered tools. */
  Map<String, ToolDefinition> getAllTools();

  /** Get a tool definition by name. */
  ToolDefinition getTool(String toolName);

  /** Get tools by risk level. */
  List<ToolDefinition> getToolsByRiskLevel(ToolRiskLevel riskLevel);

  /** Check if a tool is registered. */
  boolean isToolRegistered(String toolName);

  /** Get names of all registered tools. */
  List<String> getToolNames();
}