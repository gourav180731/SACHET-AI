package ai.sachet.service;

/**
 * Metadata for a registered tool/operation.
 * Corresponds to AGENTS.md §31.
 */
public class ToolDefinition {

  private final String name;
  private final String purpose;
  private final ToolRiskLevel riskLevel;
  private final String requiredConfirmation;
  private final boolean requiresAuthorization;
  private final String description;

  public ToolDefinition(String name, String purpose, ToolRiskLevel riskLevel,
      String requiredConfirmation, boolean requiresAuthorization, String description) {
    this.name = name;
    this.purpose = purpose;
    this.riskLevel = riskLevel;
    this.requiredConfirmation = requiredConfirmation;
    this.requiresAuthorization = requiresAuthorization;
    this.description = description;
  }

  public String getName() { return name; }
  public String getPurpose() { return purpose; }
  public ToolRiskLevel getRiskLevel() { return riskLevel; }
  public String getRequiredConfirmation() { return requiredConfirmation; }
  public boolean requiresAuthorization() { return requiresAuthorization; }
  public String getDescription() { return description; }
}