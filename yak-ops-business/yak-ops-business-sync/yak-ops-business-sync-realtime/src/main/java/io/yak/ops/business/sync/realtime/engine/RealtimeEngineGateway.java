package io.yak.ops.business.sync.realtime.engine;

import com.fasterxml.jackson.databind.JsonNode;

/** Yak Ops-side adapter for Flink CDC CLI submission and Flink REST operations. */
public interface RealtimeEngineGateway {

  JsonNode health();

  JsonNode capabilities();

  ValidationResult validate(String pipelineYaml);

  DeployResult deploy(RealtimeDeployRequest request);

  RuntimeStatus status(String jobId);

  void stop(String jobId);

  String logs(String jobId, int tailLines);

  JsonNode checkpoints(String jobId);

  JsonNode metrics(String jobId);

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
