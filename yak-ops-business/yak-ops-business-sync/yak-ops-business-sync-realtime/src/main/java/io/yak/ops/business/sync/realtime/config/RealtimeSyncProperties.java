package io.yak.ops.business.sync.realtime.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("yak.sync.realtime")
public class RealtimeSyncProperties {

  private boolean enabled = true;
  private String baseUrl = "http://localhost:8080";
  private Duration connectTimeout = Duration.ofSeconds(3);
  private Duration requestTimeout = Duration.ofSeconds(15);
  private int maxLogLines = 1_000;
  private int reconcileFailureThreshold = 3;
  private int reconcileLeaseSeconds = 30;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
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
}
