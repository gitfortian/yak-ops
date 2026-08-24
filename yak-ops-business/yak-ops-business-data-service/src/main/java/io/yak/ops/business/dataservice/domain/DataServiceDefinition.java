package io.yak.ops.business.dataservice.domain;

import io.yak.ops.business.dataservice.domain.access.AuthMode;
import java.time.LocalDateTime;

/** Aggregate root for the persisted Data Service definition and its published runtime binding. */
public final class DataServiceDefinition {

  private Long id;
  private DataServiceSettings settings;
  private PublishedRuntimeSnapshot runtimeSnapshot;
  private SourceReference sourceReference;
  private RuntimePolicy runtimePolicy;
  private AuthMode authMode;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;

  private DataServiceDefinition() {}

  public static DataServiceDefinition create(
      DataServiceSettings settings,
      PublishedRuntimeSnapshot runtimeSnapshot,
      SourceReference sourceReference,
      RuntimePolicy runtimePolicy,
      LocalDateTime now) {
    DataServiceDefinition definition = new DataServiceDefinition();
    definition.settings = requireSettings(settings);
    definition.runtimeSnapshot = requireRuntime(runtimeSnapshot);
    definition.sourceReference = requireSource(sourceReference);
    definition.runtimePolicy = runtimePolicy == null ? RuntimePolicy.defaults(true) : runtimePolicy;
    definition.authMode = AuthMode.NONE;
    definition.createTime = now;
    definition.updateTime = now;
    return definition;
  }

  public static DataServiceDefinition restore(
      Long id,
      DataServiceSettings settings,
      PublishedRuntimeSnapshot runtimeSnapshot,
      SourceReference sourceReference,
      RuntimePolicy runtimePolicy,
      AuthMode authMode,
      LocalDateTime createTime,
      LocalDateTime updateTime) {
    DataServiceDefinition definition = new DataServiceDefinition();
    definition.id = id;
    definition.settings = requireSettings(settings);
    definition.runtimeSnapshot = requireRuntime(runtimeSnapshot);
    definition.sourceReference = requireSource(sourceReference);
    definition.runtimePolicy = runtimePolicy == null ? RuntimePolicy.defaults(false) : runtimePolicy;
    definition.authMode = authMode == null ? AuthMode.NONE : authMode;
    definition.createTime = createTime;
    definition.updateTime = updateTime;
    return definition;
  }

  public void updateSettings(DataServiceSettings settings, LocalDateTime now) {
    this.settings = requireSettings(settings);
    this.updateTime = now;
  }

  public void republish(
      DataServiceSettings settings,
      PublishedRuntimeSnapshot runtimeSnapshot,
      SourceReference sourceReference,
      LocalDateTime now) {
    this.settings = requireSettings(settings);
    this.runtimeSnapshot = requireRuntime(runtimeSnapshot);
    this.sourceReference = requireSource(sourceReference);
    this.updateTime = now;
  }

  public void setEnabled(boolean enabled, LocalDateTime now) {
    DataServiceSettings current = settings;
    this.settings = new DataServiceSettings(
        current.name(), current.path(), current.maxRows(), current.timeoutSeconds(), enabled,
        current.description(), current.paginationEnabled());
    this.updateTime = now;
  }

  public void setAuthMode(AuthMode mode, LocalDateTime now) {
    this.authMode = mode == null ? AuthMode.NONE : mode;
    this.updateTime = now;
  }

  public void updateRuntimePolicy(RuntimePolicy policy, LocalDateTime now) {
    if (policy == null) throw new IllegalArgumentException("Runtime policy must not be null");
    this.runtimePolicy = policy;
    this.updateTime = now;
  }

  private static DataServiceSettings requireSettings(DataServiceSettings value) {
    if (value == null) throw new IllegalArgumentException("Data Service settings must not be null");
    return value;
  }

  private static PublishedRuntimeSnapshot requireRuntime(PublishedRuntimeSnapshot value) {
    if (value == null) throw new IllegalArgumentException("Published runtime snapshot must not be null");
    return value;
  }

  private static SourceReference requireSource(SourceReference value) {
    if (value == null) throw new IllegalArgumentException("Source reference must not be null");
    return value;
  }

  public Long id() { return id; }
  public DataServiceSettings settings() { return settings; }
  public PublishedRuntimeSnapshot runtimeSnapshot() { return runtimeSnapshot; }
  public SourceReference sourceReference() { return sourceReference; }
  public RuntimePolicy runtimePolicy() { return runtimePolicy; }
  public AuthMode authMode() { return authMode; }
  public LocalDateTime createTime() { return createTime; }
  public LocalDateTime updateTime() { return updateTime; }
}
