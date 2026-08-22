package io.yak.ops.business.sync.realtime.engine;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;

/** Yak Ops-side adapter for Flink CDC CLI submission and Flink REST operations. */
public interface RealtimeEngineGateway {

  /** Compatibility path that resolves the current application/default runtime settings. */
  JsonNode health();

  JsonNode health(ComputeEnvironmentSnapshot environment);

  /** Compatibility path that resolves the current application/default runtime settings. */
  JsonNode capabilities();

  JsonNode capabilities(ComputeEnvironmentSnapshot environment);

  /** Compatibility path that resolves the current application/default runtime settings. */
  ValidationResult validate(String pipelineYaml);

  ValidationResult validate(ComputeEnvironmentSnapshot environment, String pipelineYaml);

  /** Compatibility path that resolves the current application/default runtime settings. */
  DeployResult deploy(RealtimeDeployRequest request);

  DeployResult deploy(ComputeEnvironmentSnapshot environment, RealtimeDeployRequest request);

  /** Compatibility path that resolves the current application/default runtime settings. */
  RuntimeStatus status(String jobId);

  RuntimeStatus status(ComputeEnvironmentSnapshot environment, String jobId);

  /** Compatibility path that resolves the current application/default runtime settings. */
  void stop(String jobId);

  void stop(ComputeEnvironmentSnapshot environment, String jobId);

  /** Compatibility path that resolves the current application/default runtime settings. */
  String logs(String jobId, int tailLines);

  String logs(ComputeEnvironmentSnapshot environment, String jobId, int tailLines);

  /** Compatibility path that resolves the current application/default runtime settings. */
  JsonNode checkpoints(String jobId);

  JsonNode checkpoints(ComputeEnvironmentSnapshot environment, String jobId);

  /** Compatibility path that resolves the current application/default runtime settings. */
  JsonNode metrics(String jobId);

  JsonNode metrics(ComputeEnvironmentSnapshot environment, String jobId);

  record ValidationResult(boolean valid, String deliverySemantics) {}

  record DeployResult(String jobId, String deliverySemantics) {}

  record RuntimeStatus(String jobId, State state) {
    public enum State {
      NONE,
      RUNNING,
      TERMINATED,
      UNKNOWN
    }
  }
}
