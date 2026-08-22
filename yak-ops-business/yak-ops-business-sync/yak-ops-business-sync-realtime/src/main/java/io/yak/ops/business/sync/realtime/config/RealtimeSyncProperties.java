package io.yak.ops.business.sync.realtime.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("yak.sync.realtime")
public class RealtimeSyncProperties {

  public enum SubmissionMode {
    LOCAL,
    SSH
  }

  private boolean enabled = true;
  private String restUrl = "http://127.0.0.1:8081";
  private String flinkHome = "/opt/flink";
  private String flinkCdcHome = "/opt/flink-cdc";
  private String javaHome;
  private String workDirectory = "./data/realtime-sync";
  private String flinkVersion = "1.20.5";
  private String flinkCdcVersion = "3.6.0";
  private SubmissionMode submissionMode = SubmissionMode.LOCAL;
  private final Ssh ssh = new Ssh();
  private Duration connectTimeout = Duration.ofSeconds(3);
  private Duration requestTimeout = Duration.ofSeconds(15);
  private Duration submitTimeout = Duration.ofSeconds(60);
  private int maxLogLines = 1_000;
  private int reconcileFailureThreshold = 3;
  private int reconcileLeaseSeconds = 30;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getRestUrl() {
    return restUrl;
  }

  public void setRestUrl(String restUrl) {
    this.restUrl = restUrl;
  }

  public String getFlinkHome() {
    return flinkHome;
  }

  public void setFlinkHome(String flinkHome) {
    this.flinkHome = flinkHome;
  }

  public String getFlinkCdcHome() {
    return flinkCdcHome;
  }

  public void setFlinkCdcHome(String flinkCdcHome) {
    this.flinkCdcHome = flinkCdcHome;
  }

  public String getJavaHome() {
    return javaHome;
  }

  public void setJavaHome(String javaHome) {
    this.javaHome = javaHome;
  }

  public String getWorkDirectory() {
    return workDirectory;
  }

  public void setWorkDirectory(String workDirectory) {
    this.workDirectory = workDirectory;
  }

  public String getFlinkVersion() {
    return flinkVersion;
  }

  public void setFlinkVersion(String flinkVersion) {
    this.flinkVersion = flinkVersion;
  }

  public String getFlinkCdcVersion() {
    return flinkCdcVersion;
  }

  public void setFlinkCdcVersion(String flinkCdcVersion) {
    this.flinkCdcVersion = flinkCdcVersion;
  }

  public SubmissionMode getSubmissionMode() {
    return submissionMode;
  }

  public void setSubmissionMode(SubmissionMode submissionMode) {
    this.submissionMode = submissionMode == null ? SubmissionMode.LOCAL : submissionMode;
  }

  public Ssh getSsh() {
    return ssh;
  }

  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(Duration connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
  }

  public Duration getSubmitTimeout() {
    return submitTimeout;
  }

  public void setSubmitTimeout(Duration submitTimeout) {
    this.submitTimeout = submitTimeout;
  }

  public int getReconcileFailureThreshold() {
    return reconcileFailureThreshold;
  }

  public void setReconcileFailureThreshold(int reconcileFailureThreshold) {
    this.reconcileFailureThreshold = reconcileFailureThreshold;
  }

  public int getReconcileLeaseSeconds() {
    return reconcileLeaseSeconds;
  }

  public void setReconcileLeaseSeconds(int reconcileLeaseSeconds) {
    this.reconcileLeaseSeconds = reconcileLeaseSeconds;
  }

  public int getMaxLogLines() {
    return maxLogLines;
  }

  public void setMaxLogLines(int maxLogLines) {
    this.maxLogLines = maxLogLines;
  }

  /** OpenSSH client settings used only when submission-mode=SSH. */
  public static class Ssh {
    private String executable = "ssh";
    private String host;
    private int port = 22;
    private String user;
    private String identityFile;
    private String knownHostsFile;
    private boolean strictHostKeyChecking = true;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private String remoteRestAddress;
    private Integer remoteRestPort;

    public String getExecutable() {
      return executable;
    }

    public void setExecutable(String executable) {
      this.executable = executable;
    }

    public String getHost() {
      return host;
    }

    public void setHost(String host) {
      this.host = host;
    }

    public int getPort() {
      return port;
    }

    public void setPort(int port) {
      this.port = port;
    }

    public String getUser() {
      return user;
    }

    public void setUser(String user) {
      this.user = user;
    }

    public String getIdentityFile() {
      return identityFile;
    }

    public void setIdentityFile(String identityFile) {
      this.identityFile = identityFile;
    }

    public String getKnownHostsFile() {
      return knownHostsFile;
    }

    public void setKnownHostsFile(String knownHostsFile) {
      this.knownHostsFile = knownHostsFile;
    }

    public boolean isStrictHostKeyChecking() {
      return strictHostKeyChecking;
    }

    public void setStrictHostKeyChecking(boolean strictHostKeyChecking) {
      this.strictHostKeyChecking = strictHostKeyChecking;
    }

    public Duration getConnectTimeout() {
      return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
      this.connectTimeout = connectTimeout;
    }

    public String getRemoteRestAddress() {
      return remoteRestAddress;
    }

    public void setRemoteRestAddress(String remoteRestAddress) {
      this.remoteRestAddress = remoteRestAddress;
    }

    public Integer getRemoteRestPort() {
      return remoteRestPort;
    }

    public void setRemoteRestPort(Integer remoteRestPort) {
      this.remoteRestPort = remoteRestPort;
    }
  }
}
