package io.yak.ops.business.sync.realtime.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.engine.RealtimeDeployRequest.CredentialBinding;
import io.yak.ops.business.sync.realtime.repository.RealtimeRuntimeIdentityStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RecoverableRealtimeEngineGatewayTest {

  @Test
  void bindsIdentityBeforeDelegatingSubmission() {
    FlinkCdcEngineGateway delegate = mock(FlinkCdcEngineGateway.class);
    RealtimeRuntimeIdentityStore identityStore = mock(RealtimeRuntimeIdentityStore.class);
    RecoverableRealtimeEngineGateway gateway =
        new RecoverableRealtimeEngineGateway(delegate, identityStore);
    ComputeEnvironmentSnapshot environment = environment();
    CredentialBinding source = new CredentialBinding("source", "source-secret".toCharArray());
    CredentialBinding sink = new CredentialBinding("sink", "sink-secret".toCharArray());
    RealtimeDeployRequest request =
        new RealtimeDeployRequest("pipeline:\n  name: demo\n", "deploy-key", source, sink);
    when(delegate.deploy(eq(environment), any()))
        .thenReturn(
            new RealtimeEngineGateway.DeployResult(
                "0123456789abcdef0123456789abcdef", "at-least-once"));

    gateway.deploy(environment, request);

    String runtimeName = RealtimeRuntimeIdentity.jobName("deploy-key");
    verify(identityStore).bind("deploy-key", runtimeName);
    ArgumentCaptor<RealtimeDeployRequest> captured =
        ArgumentCaptor.forClass(RealtimeDeployRequest.class);
    verify(delegate).deploy(eq(environment), captured.capture());
    assertThat(captured.getValue().pipelineYaml()).contains("name: " + runtimeName);
    assertThat(captured.getValue().source()).isSameAs(source);
    assertThat(captured.getValue().sink()).isSameAs(sink);
  }

  private ComputeEnvironmentSnapshot environment() {
    return new ComputeEnvironmentSnapshot(
        3L,
        "test-env",
        ComputeEnvironment.ENGINE_FLINK_CDC,
        ComputeEnvironment.DEPLOYMENT_REMOTE,
        ComputeEnvironment.SUBMITTER_LOCAL,
        new RuntimeConfig(
            "http://127.0.0.1:8081", "/opt/flink", "/opt/flink-cdc", null, "1.20.5", "3.6.0"),
        1);
  }
}
