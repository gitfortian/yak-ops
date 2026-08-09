package io.yak.ops.business.workflow.persistence;

import java.util.List;

/** Fallback used when the shared Yak business database is explicitly disabled. */
public final class NoopWorkflowDefinitionPersistence implements WorkflowDefinitionPersistence {

  public static final NoopWorkflowDefinitionPersistence INSTANCE =
      new NoopWorkflowDefinitionPersistence();

  private NoopWorkflowDefinitionPersistence() {
  }

  @Override
  public List<DefinitionRecord> loadDefinitions() {
    return List.of();
  }

  @Override
  public List<VersionRecord> loadVersions(String workflowId) {
    return List.of();
  }

  @Override
  public void saveDefinition(DefinitionRecord definition) {
    // In-memory WorkflowDefinitionService remains the fallback fact source.
  }

  @Override
  public void publish(DefinitionRecord definition, VersionRecord version) {
    // In-memory WorkflowDefinitionService remains the fallback fact source.
  }

  @Override
  public void deleteDefinition(String workflowId) {
    // No durable state to remove.
  }
}
