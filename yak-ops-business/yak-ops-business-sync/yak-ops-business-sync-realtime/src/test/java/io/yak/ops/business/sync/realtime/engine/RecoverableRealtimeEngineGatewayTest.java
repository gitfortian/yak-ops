package io.yak.ops.business.sync.realtime.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.realtime.repository.RealtimeRuntimeIdentityStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RecoverableRealtimeEngineGatewayTest {

  @Test
  void persistsRuntimeIdentityBeforeDelegatingSubmission() {
    FlinkCdcEngineGateway delegate = mock(FlinkCdcEngineGateway.class);
    RealtimeRuntimeIdentityStore identityStore = mock(RealtimeRuntimeIdentityStore.class);
    RecoverableRealtimeEngineGateway gateway =
        new RecoverableRealtimeEngineGateway(delegate, identityStore);
    String key = "deploy-key";
    String yaml =
        "pipeline:\n  name: display-name\nsource:\n  password: ${SECRET:source.password}\n"
            + "sink:\n  password: ${SECRET:sink.password}\n";
    when(delegate.deploy(org.mockito.ArgumentMatchers.any()))
        .thenReturn(new RealtimeEngineGateway.DeployResult(
            "0123456789abcdef0123456789abcdef", "at-least-once"));

    try (RealtimeDeployRequest request =
        new RealtimeDeployRequest(
            yaml,
            key,
            new RealtimeDeployRequest.CredentialBinding("source", "s1"),
            new RealtimeDeployRequest.CredentialBinding("sink", "s2"))) {
      gateway.deploy(request);
    }

    String runtimeName = RealtimeRuntimeIdentity.jobName(key);
    verify(identityStore).bind(key, runtimeName);
    ArgumentCaptor<RealtimeDeployRequest> captor =
        ArgumentCaptor.forClass(RealtimeDeployRequest.class);
    verify(delegate).deploy(captor.capture());
    assertThat(captor.getValue().pipelineYaml())
        .contains("pipeline:\n  name: " + runtimeName + "\n")
        .doesNotContain("display-name");
  }
}
