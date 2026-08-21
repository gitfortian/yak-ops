package io.yak.ops.business.sync.realtime.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("yak.sync.realtime")
public class RealtimeSyncProperties {

  private boolean enabled = true;
  private String baseUrl = "http://localhost:8080";
  private Duration connectTimeout = Duration.ofSeconds(3);
  private Duration requestTimeout = Duration.ofSeconds(15);
  private String sourcePasswordEnv = "SOURCE_PASSWORD";
  private String sinkPasswordEnv = "SINK_PASSWORD";
  private int maxLogLines = 1_000;

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

  public String getSourcePasswordEnv() {
    return sourcePasswordEnv;
  }

  public void setSourcePasswordEnv(String sourcePasswordEnv) {
    this.sourcePasswordEnv = sourcePasswordEnv;
  }

  public String getSinkPasswordEnv() {
    return sinkPasswordEnv;
  }

  public void setSinkPasswordEnv(String sinkPasswordEnv) {
    this.sinkPasswordEnv = sinkPasswordEnv;
  }

  public int getMaxLogLines() {
    return maxLogLines;
  }

  public void setMaxLogLines(int maxLogLines) {
    this.maxLogLines = maxLogLines;
  }
}
