package io.yak.ops.business.sync.realtime.engine;

import java.util.Arrays;

/** Submission-scoped deployment data. Credentials must never be persisted or logged. */
public final class RealtimeDeployRequest implements AutoCloseable {

  private final String pipelineYaml;
  private final String idempotencyKey;
  private final CredentialBinding source;
  private final CredentialBinding sink;

  public RealtimeDeployRequest(
      String pipelineYaml,
      String idempotencyKey,
      CredentialBinding source,
      CredentialBinding sink) {
    this.pipelineYaml = pipelineYaml;
    this.idempotencyKey = idempotencyKey;
    this.source = source;
    this.sink = sink;
  }

  public String pipelineYaml() {
    return pipelineYaml;
  }

  public String idempotencyKey() {
    return idempotencyKey;
  }

  public CredentialBinding source() {
    return source;
  }

  public CredentialBinding sink() {
    return sink;
  }

  @Override
  public void close() {
    source.close();
    sink.close();
  }

  @Override
  public String toString() {
    return "RealtimeDeployRequest[pipelineYaml=******, idempotencyKey="
        + idempotencyKey
        + ", source=******, sink=******]";
  }

  public static final class CredentialBinding implements AutoCloseable {
    private final String username;
    private final char[] password;

    public CredentialBinding(String username, String password) {
      this.username = username;
      this.password = password == null ? new char[0] : password.toCharArray();
    }

    public String username() {
      return username;
    }

    char[] password() {
      return password;
    }

    @Override
    public void close() {
      Arrays.fill(password, '\0');
    }

    @Override
    public String toString() {
      return "CredentialBinding[username=" + username + ", password=******]";
    }
  }
}
