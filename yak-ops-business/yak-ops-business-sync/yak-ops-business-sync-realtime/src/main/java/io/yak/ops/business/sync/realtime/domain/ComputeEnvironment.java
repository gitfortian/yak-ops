package io.yak.ops.business.sync.realtime.domain;

import java.time.LocalDateTime;

/** Runtime environment used by Yak Ops to submit realtime jobs to Flink CDC. */
public record ComputeEnvironment(
    long id,
    String name,
    String engineType,
    String deploymentMode,
    String submitterType,
    RuntimeConfig config,
    boolean enabled,
    boolean defaultEnvironment,
    int version,
    LocalDateTime createTime,
    LocalDateTime updateTime) {

  public static final String ENGINE_FLINK_CDC = "FLINK_CDC";
  public static final String DEPLOYMENT_REMOTE = "REMOTE";
  public static final String SUBMITTER_LOCAL = "LOCAL";
  public static final String SUBMITTER_SSH = "SSH";

  /**
   * Stage one intentionally stores only the settings that describe where Flink CDC runs. Yak Ops
   * operational settings such as timeouts, log retention and reconcile cadence remain application
   * level settings.
   */
  public record RuntimeConfig(
      String restUrl,
      String flinkHome,
      String flinkCdcHome,
      String javaHome,
      String flinkVersion,
      String flinkCdcVersion) {}
}
