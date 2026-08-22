package io.yak.ops.business.sync.realtime.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties.RuntimeOverrides;
import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties.SubmissionMode;
import org.junit.jupiter.api.Test;

class RealtimeSyncPropertiesTest {

  @Test
  void defaultEnvironmentOverridesRuntimeSettingsWithoutMovingWorkDirectory() {
    RealtimeSyncProperties properties = new RealtimeSyncProperties();
    properties.setRestUrl("http://bootstrap:8081");
    properties.setFlinkHome("/bootstrap/flink");
    properties.setFlinkCdcHome("/bootstrap/cdc");
    properties.setJavaHome("/bootstrap/java");
    properties.setFlinkVersion("1.20.0");
    properties.setFlinkCdcVersion("3.5.0");
    properties.setWorkDirectory("/var/lib/yak/realtime");

    properties.applyRuntimeOverrides(
        new RuntimeOverrides(
            "http://production:8081",
            "/opt/flink",
            "/opt/flink-cdc",
            "/opt/java",
            "1.20.5",
            "3.6.0",
            SubmissionMode.LOCAL));

    assertThat(properties.getRestUrl()).isEqualTo("http://production:8081");
    assertThat(properties.getFlinkHome()).isEqualTo("/opt/flink");
    assertThat(properties.getFlinkCdcHome()).isEqualTo("/opt/flink-cdc");
    assertThat(properties.getJavaHome()).isEqualTo("/opt/java");
    assertThat(properties.getFlinkVersion()).isEqualTo("1.20.5");
    assertThat(properties.getFlinkCdcVersion()).isEqualTo("3.6.0");
    assertThat(properties.getWorkDirectory()).isEqualTo("/var/lib/yak/realtime");

    properties.clearRuntimeOverrides();

    assertThat(properties.getRestUrl()).isEqualTo("http://bootstrap:8081");
    assertThat(properties.getFlinkHome()).isEqualTo("/bootstrap/flink");
    assertThat(properties.getFlinkCdcHome()).isEqualTo("/bootstrap/cdc");
    assertThat(properties.getJavaHome()).isEqualTo("/bootstrap/java");
    assertThat(properties.getFlinkVersion()).isEqualTo("1.20.0");
    assertThat(properties.getFlinkCdcVersion()).isEqualTo("3.5.0");
  }
}
