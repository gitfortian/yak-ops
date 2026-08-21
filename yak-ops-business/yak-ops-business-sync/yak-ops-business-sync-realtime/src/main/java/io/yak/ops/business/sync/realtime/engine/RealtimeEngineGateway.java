package io.yak.ops.business.sync.realtime.engine;

import com.fasterxml.jackson.databind.JsonNode;

/** Stable Yak Ops-side adapter for the fixed Yak CDC Runtime Gateway. */
public interface RealtimeEngineGateway {

  JsonNode health();

  JsonNode capabilities();

  ValidationResult validate(String pipelineYaml);

  DeployResult deploy(RealtimeDeployRequest request);

  RuntimeStatus status();

  void stop();

  String logs(int tailLines);

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
