package io.yak.ops.business.sync.realtime.environment;

import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentDiagnosis;
import java.util.List;
import org.springframework.stereotype.Service;

/** Stable application entry for realtime compute environment use-cases. */
@Service("computeEnvironmentApplicationService")
public class ComputeEnvironmentService {

  private final io.yak.ops.business.sync.realtime.service.ComputeEnvironmentService environments;

  public ComputeEnvironmentService(
      io.yak.ops.business.sync.realtime.service.ComputeEnvironmentService environments) {
    this.environments = environments;
  }

  public List<ComputeEnvironment> list() {
    return environments.list();
  }

  public ComputeEnvironment get(long id) {
    return environments.get(id);
  }

  public long create(
      String name,
      String submitterType,
      RuntimeConfig config,
      boolean enabled,
      boolean makeDefault) {
    return environments.create(name, submitterType, config, enabled, makeDefault);
  }

  public ComputeEnvironmentDiagnosis diagnose(long id) {
    return environments.diagnose(id);
  }

  public ComputeEnvironmentDiagnosis diagnosePreview(
      String name, String submitterType, RuntimeConfig config) {
    return environments.diagnosePreview(name, submitterType, config);
  }

  public void update(
      long id,
      String name,
      String submitterType,
      RuntimeConfig config,
      boolean enabled,
      boolean makeDefault) {
    environments.update(id, name, submitterType, config, enabled, makeDefault);
  }

  public void setEnabled(long id, boolean enabled) {
    environments.setEnabled(id, enabled);
  }

  public void setDefault(long id) {
    environments.setDefault(id);
  }

  public void delete(long id) {
    environments.delete(id);
  }
}
