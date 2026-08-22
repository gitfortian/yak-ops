package io.yak.ops.business.sync.realtime.engine;

import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/** Executes Flink CDC CLI remotely through the operating system OpenSSH client. */
final class SshFlinkCdcCommandRunner {

  private static final Pattern SSH_USER = Pattern.compile("[A-Za-z0-9._-]+");
  private static final Pattern SSH_HOST = Pattern.compile("[A-Za-z0-9._:%-]+");

  private final RealtimeSyncProperties properties;

  SshFlinkCdcCommandRunner(RealtimeSyncProperties properties) {
    this.properties = properties;
  }

  String configurationError() {
    RealtimeSyncProperties.Ssh ssh = properties.getSsh();
    if (!StringUtils.hasText(ssh.getExecutable())) {
      return "SSH executable 不能为空";
    }
    if (!StringUtils.hasText(ssh.getHost()) || !SSH_HOST.matcher(ssh.getHost()).matches()) {
      return "SSH host 未配置或格式无效";
    }
    if (!StringUtils.hasText(ssh.getUser()) || !SSH_USER.matcher(ssh.getUser()).matches()) {
      return "SSH user 未配置或格式无效";
    }
    if (ssh.getPort() < 1 || ssh.getPort() > 65535) {
      return "SSH port 必须在 1-65535 之间";
    }
    if (!absoluteUnixPath(properties.getFlinkHome())
        || !absoluteUnixPath(properties.getFlinkCdcHome())) {
      return "SSH 模式下 flink-home 和 flink-cdc-home 必须是远端 Linux 绝对路径";
    }
    if (StringUtils.hasText(properties.getJavaHome()) && !absoluteUnixPath(properties.getJavaHome())) {
      return "SSH 模式下 java-home 必须是远端 Linux 绝对路径";
    }
    Integer remoteRestPort = ssh.getRemoteRestPort();
    if (remoteRestPort != null && (remoteRestPort < 1 || remoteRestPort > 65535)) {
      return "SSH remote-rest-port 必须在 1-65535 之间";
    }
    return null;
  }

  String endpoint() {
    RealtimeSyncProperties.Ssh ssh = properties.getSsh();
    return StringUtils.hasText(ssh.getUser()) && StringUtils.hasText(ssh.getHost())
        ? ssh.getUser() + "@" + ssh.getHost() + ":" + ssh.getPort()
        : null;
  }

  void validateReady(URI restUri) {
    String error = configurationError();
    if (error != null) {
      throw failure(error, false, null);
    }
    remoteRestPort(restUri);
    Process process = null;
    try {
      process =
          new ProcessBuilder(sshCommand(remoteProbeCommand(restUri)))
              .redirectErrorStream(true)
              .redirectOutput(ProcessBuilder.Redirect.DISCARD)
              .start();
      process.getOutputStream().close();
      Duration connectTimeout = positiveDuration(properties.getSsh().getConnectTimeout(), Duration.ofSeconds(5));
      Duration timeout = connectTimeout.plusSeconds(5);
      if (!process.waitFor(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS)) {
        destroy(process);
        throw failure("SSH 远端环境探测超时", false, null);
      }
      if (process.exitValue() != 0) {
        String message =
            process.exitValue() == 255
                ? "SSH 连接或认证失败，请检查 host key、用户和密钥配置"
                : "SSH 已连接，但远端 Flink/Flink CDC/Java 环境未通过检查";
        throw failure(message + "，exitCode=" + process.exitValue(), false, null);
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      destroy(process);
      throw failure("SSH 远端环境探测被中断", false, exception);
    } catch (IOException exception) {
      destroy(process);
      throw failure("无法启动 OpenSSH 客户端：" + exception.getMessage(), false, exception);
    }
  }

  ExecutionResult submit(String pipelineYaml, Path outputLog, URI restUri, Duration timeout) {
    String error = configurationError();
    if (error != null) {
      throw failure(error, false, null);
    }
    Duration effectiveTimeout = positiveDuration(timeout, Duration.ofSeconds(60));
    Process process = null;
    boolean started = false;
    try {
      ProcessBuilder builder =
          new ProcessBuilder(sshCommand(remoteSubmitCommand(restUri)))
              .redirectErrorStream(true)
              .redirectOutput(outputLog.toFile());
      process = builder.start();
      started = true;
      try (OutputStream stdin = process.getOutputStream()) {
        stdin.write(pipelineYaml.getBytes(StandardCharsets.UTF_8));
        stdin.flush();
      }
      if (!process.waitFor(Math.max(1, effectiveTimeout.toMillis()), TimeUnit.MILLISECONDS)) {
        destroy(process);
        throw failure("SSH Flink CDC 提交超时，远端结果不确定，请通过状态对账确认", true, null);
      }
      int exitCode = process.exitValue();
      return new ExecutionResult(exitCode, exitCode == 255);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      destroy(process);
      throw failure("SSH Flink CDC 提交被中断，远端结果不确定", started, exception);
    } catch (IOException exception) {
      destroy(process);
      throw failure(
          started
              ? "SSH 连接在提交过程中异常，远端结果不确定"
              : "无法启动 OpenSSH 客户端：" + exception.getMessage(),
          started,
          exception);
    }
  }

  private List<String> sshCommand(String remoteCommand) {
    RealtimeSyncProperties.Ssh ssh = properties.getSsh();
    List<String> command = new ArrayList<>();
    command.add(ssh.getExecutable());
    command.add("-o");
    command.add("BatchMode=yes");
    command.add("-o");
    command.add("ConnectionAttempts=1");
    command.add("-o");
    command.add("ConnectTimeout=" + seconds(ssh.getConnectTimeout()));
    command.add("-o");
    command.add("ServerAliveInterval=15");
    command.add("-o");
    command.add("ServerAliveCountMax=2");
    command.add("-o");
    command.add("LogLevel=ERROR");
    command.add("-o");
    command.add(
        "StrictHostKeyChecking=" + (ssh.isStrictHostKeyChecking() ? "yes" : "accept-new"));
    if (StringUtils.hasText(ssh.getKnownHostsFile())) {
      command.add("-o");
      command.add("UserKnownHostsFile=" + ssh.getKnownHostsFile());
    }
    if (StringUtils.hasText(ssh.getIdentityFile())) {
      command.add("-i");
      command.add(ssh.getIdentityFile());
    }
    command.add("-p");
    command.add(Integer.toString(ssh.getPort()));
    command.add(ssh.getUser() + "@" + ssh.getHost());
    command.add(remoteCommand);
    return command;
  }

  private String remoteProbeCommand(URI restUri) {
    String cdc = remoteCdcCli();
    StringBuilder command = new StringBuilder("set -eu; ");
    command.append("test -x ").append(shellQuote(cdc)).append("; ");
    command.append("test -d ").append(shellQuote(properties.getFlinkHome())).append("; ");
    command.append("command -v mktemp >/dev/null 2>&1; ");
    if (StringUtils.hasText(properties.getJavaHome())) {
      command
          .append("test -x ")
          .append(shellQuote(properties.getJavaHome() + "/bin/java"))
          .append("; ");
    }
    command.append("test -n ").append(shellQuote(remoteRestAddress(restUri))).append("; ");
    command.append("echo YAK_REALTIME_SSH_READY");
    return command.toString();
  }

  private String remoteSubmitCommand(URI restUri) {
    StringBuilder command = new StringBuilder();
    command.append("set -eu; umask 077; ");
    command.append("tmp=$(mktemp \"${TMPDIR:-/tmp}/yak-ops-cdc.XXXXXX.yaml\"); ");
    command.append("cleanup(){ rm -f \"$tmp\"; }; trap cleanup 0 HUP INT TERM; ");
    command.append("cat > \"$tmp\"; ");
    command.append("export FLINK_HOME=").append(shellQuote(properties.getFlinkHome())).append("; ");
    if (StringUtils.hasText(properties.getJavaHome())) {
      command.append("export JAVA_HOME=").append(shellQuote(properties.getJavaHome())).append("; ");
    }
    command.append(shellQuote(remoteCdcCli())).append(" \"$tmp\"");
    command.append(" --flink-home ").append(shellQuote(properties.getFlinkHome()));
    command.append(" --target remote");
    command
        .append(" ")
        .append(shellQuote("-Drest.address=" + remoteRestAddress(restUri)));
    command.append(" ").append(shellQuote("-Drest.port=" + remoteRestPort(restUri)));
    return command.toString();
  }

  private String remoteCdcCli() {
    String home = properties.getFlinkCdcHome().replaceAll("/+$", "");
    return home + "/bin/flink-cdc.sh";
  }

  private String remoteRestAddress(URI restUri) {
    String value =
        StringUtils.hasText(properties.getSsh().getRemoteRestAddress())
            ? properties.getSsh().getRemoteRestAddress().trim()
            : restUri.getHost();
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException("SSH remote REST address 不能为空");
    }
    return value;
  }

  private int remoteRestPort(URI restUri) {
    Integer configured = properties.getSsh().getRemoteRestPort();
    if (configured != null) {
      if (configured < 1 || configured > 65535) {
        throw new IllegalArgumentException("ssh.remote-rest-port 必须在 1-65535 之间");
      }
      return configured;
    }
    if (restUri.getPort() > 0) {
      return restUri.getPort();
    }
    return "https".equalsIgnoreCase(restUri.getScheme()) ? 443 : 80;
  }

  private boolean absoluteUnixPath(String value) {
    return StringUtils.hasText(value) && value.startsWith("/");
  }

  private Duration positiveDuration(Duration value, Duration fallback) {
    return value == null || value.isNegative() || value.isZero() ? fallback : value;
  }

  private int seconds(Duration value) {
    Duration effective = positiveDuration(value, Duration.ofSeconds(5));
    return (int) Math.max(1, Math.min(Integer.MAX_VALUE, (effective.toMillis() + 999) / 1000));
  }

  private String shellQuote(String value) {
    if (value == null) {
      throw new IllegalArgumentException("远端命令参数不能为空");
    }
    return "'" + value.replace("'", "'\"'\"'") + "'";
  }

  private void destroy(Process process) {
    if (process == null || !process.isAlive()) {
      return;
    }
    process.destroy();
    try {
      if (!process.waitFor(2, TimeUnit.SECONDS)) {
        process.destroyForcibly();
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
    }
  }

  private RealtimeEngineException failure(String message, boolean uncertain, Throwable cause) {
    return new RealtimeEngineException(message, uncertain, null, cause);
  }

  record ExecutionResult(int exitCode, boolean uncertain) {}
}
