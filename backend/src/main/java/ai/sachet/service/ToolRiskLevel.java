package ai.sachet.service;

/** Risk level for registered tools, determining confirmation requirements.
 * Corresponds to AGENTS.md §31.
 */
public enum ToolRiskLevel {
  LOW,
  MEDIUM,
  HIGH,
  CRITICAL
}