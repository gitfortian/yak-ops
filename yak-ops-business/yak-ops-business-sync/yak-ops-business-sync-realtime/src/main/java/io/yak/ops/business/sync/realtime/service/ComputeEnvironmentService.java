package io.yak.ops.business.sync.realtime.service;

import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties;
import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties.RuntimeOverrides;
import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties.SubmissionMode;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.SshConfig;
import io.yak.ops.business.sync.realtime.repository.ComputeEnvironmentStore;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/** Manages the user-facing runtime environment model for realtime Flink CDC. */
@Service
@DependsOn("realtimeSyncFlyway")
public class ComputeEnvironmentService {

  private static final String BOOTSTRAP_NAME = "默认实时环境";
  private static final Pattern SSH_USER = Pattern.compile("[A-Za-z0-9._-]+");
  private static final Pattern SSH_HOST = Pattern.compile("[A-Za-z0-9._:%-]+");

  private final ComputeEnvironmentStore store;
  private final RealtimeSyncProperties properties;
  private final TransactionTemplate transactions;

  public ComputeEnvironmentService(
      ComputeEnvironmentStore store,
      RealtimeSyncProperties properties,
      @Qualifier("yakBusinessTransactionManager") PlatformTransactionManager transactionManager) {
    this.store = store;
    this.properties = properties;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  @PostConstruct
  void initialize() {
    bootstrapDefaultEnvironment();
    backfillLegacySshConfigurations();
    backfillLegacyRuntimeBindings();
    refreshRuntimeOverrides();
  }

  public List<ComputeEnvironment> list() {
    return store.list();
  }

  public ComputeEnvironment get(long id) {
    return require(id);
  }

  public long create(
      String name,
      String submitterType,
      RuntimeConfig config,
      boolean enabled,
      boolean makeDefault) {
    String normalizedName = normalizeName(name);
    String normalizedSubmitter = normalizeSubmitterType(submitterType, ComputeEnvironment.SUBMITTER_LOCAL);
    RuntimeConfig normalizedConfig = normalizeConfig(config, normalizedSubmitter);
    if (makeDefault && !enabled) {
      throw new IllegalArgumentException("默认运行环境必须保持启用");
    }

    try {
      Long id =
          transactions.execute(
              status -> {
                if (makeDefault) {
                  store.clearDefault();
                }
                return store.insert(
                    normalizedName,
                    ComputeEnvironment.ENGINE_FLINK_CDC,
                    ComputeEnvironment.DEPLOYMENT_REMOTE,
                    normalizedSubmitter,
                    normalizedConfig,
                    enabled,
                    makeDefault);
              });
      if (makeDefault) {
        refreshRuntimeOverrides();
      }
      return Objects.requireNonNull(id, "新增运行环境失败");
    } catch (DuplicateKeyException exception) {
      throw new IllegalArgumentException("运行环境名称已存在：" + normalizedName, exception);
    }
  }

  public void update(
      long id,
      String name,
      String submitterType,
      RuntimeConfig config,
      boolean enabled,
      boolean makeDefault) {
    ComputeEnvironment current = require(id);
    String normalizedName = normalizeName(name);
    String normalizedSubmitter =
        normalizeSubmitterType(submitterType, current.submitterType());
    RuntimeConfig requestedConfig = preserveExistingSshConfig(current, normalizedSubmitter, config);
    RuntimeConfig normalizedConfig = normalizeConfig(requestedConfig, normalizedSubmitter);
    boolean switchingDefault = makeDefault && !current.defaultEnvironment();

    if (current.defaultEnvironment() && !enabled) {
      throw new IllegalStateException("默认运行环境不能停用，请先切换默认环境");
    }
    if (switchingDefault && !enabled) {
      throw new IllegalArgumentException("默认运行环境必须保持启用");
    }

    try {
      transactions.executeWithoutResult(
          status -> {
            store.update(id, normalizedName, normalizedSubmitter, normalizedConfig, enabled);
            if (switchingDefault) {
              store.clearDefault();
              store.setDefault(id);
            }
          });
    } catch (DuplicateKeyException exception) {
      throw new IllegalArgumentException("运行环境名称已存在：" + normalizedName, exception);
    }
    if (current.defaultEnvironment() || switchingDefault) {
      refreshRuntimeOverrides();
    }
  }

  public void setEnabled(long id, boolean enabled) {
    ComputeEnvironment current = require(id);
    if (current.defaultEnvironment() && !enabled) {
      throw new IllegalStateException("默认运行环境不能停用，请先切换默认环境");
    }
    store.setEnabled(id, enabled);
  }

  public void setDefault(long id) {
    ComputeEnvironment target = require(id);
    if (target.defaultEnvironment()) {
      return;
    }
    if (!target.enabled()) {
      throw new IllegalStateException("只有已启用的运行环境才能设为默认环境");
    }
    transactions.executeWithoutResult(
        status -> {
          store.clearDefault();
          store.setDefault(id);
        });
    refreshRuntimeOverrides();
  }

  public void delete(long id) {
    ComputeEnvironment current = require(id);
    if (current.defaultEnvironment()) {
      throw new IllegalStateException("默认运行环境不能删除，请先切换默认环境");
    }
    if (store.hasBoundRealtimeJobs(id)) {
      throw new IllegalStateException("运行环境仍被实时同步任务引用，请先将这些任务切换到其他运行环境");
    }
    store.delete(id);
  }

  /**
   * The application/default override remains as a compatibility fallback for older integrations.
   * Task lifecycle operations use the task/deployment environment explicitly.
   */
  @Scheduled(fixedDelayString = "${yak.sync.realtime.environment-refresh-delay:5000}")
  public void refreshRuntimeOverrides() {
    ComputeEnvironment environment = store.defaultEnvironment().orElse(null);
    if (environment == null) {
      properties.clearRuntimeOverrides();
      return;
    }
    RuntimeConfig config = environment.config();
    SubmissionMode mode = submissionMode(environment.submitterType());
    properties.applyRuntimeOverrides(
        new RuntimeOverrides(
            config.restUrl(),
            config.flinkHome(),
            config.flinkCdcHome(),
            config.javaHome(),
            config.flinkVersion(),
            config.flinkCdcVersion(),
            mode));
  }

  private void bootstrapDefaultEnvironment() {
    if (store.count() > 0) {
      return;
    }
    String submitterType = properties.getSubmissionMode().name();
    RuntimeConfig config =
        new RuntimeConfig(
            properties.getRestUrl(),
            properties.getFlinkHome(),
            properties.getFlinkCdcHome(),
            properties.getJavaHome(),
            properties.getFlinkVersion(),
            properties.getFlinkCdcVersion(),
            ComputeEnvironment.SUBMITTER_SSH.equals(submitterType) ? bootstrapSshConfig() : null);
    try {
      transactions.executeWithoutResult(
          status -> {
            if (store.count() == 0) {
              store.insert(
                  BOOTSTRAP_NAME,
                  ComputeEnvironment.ENGINE_FLINK_CDC,
                  ComputeEnvironment.DEPLOYMENT_REMOTE,
                  submitterType,
                  normalizeConfig(config, submitterType),
                  true,
                  true);
            }
          });
    } catch (DuplicateKeyException ignored) {
      // Another Yak Ops instance won the bootstrap race.
    }
  }

  /**
   * Stage one/two could create an SSH environment whose SSH client settings still lived only in
   * application.yml. Copy that non-secret connection metadata into config_json once so the stage
   * three editor can manage it. Historical deployment snapshots remain unchanged.
   */
  private void backfillLegacySshConfigurations() {
    SshConfig fallback = bootstrapSshConfig();
    for (ComputeEnvironment environment : store.list()) {
      RuntimeConfig config = environment.config();
      if (!ComputeEnvironment.SUBMITTER_SSH.equals(environment.submitterType())
          || config == null
          || config.ssh() != null) {
        continue;
      }
      RuntimeConfig migrated =
          new RuntimeConfig(
              config.restUrl(),
              config.flinkHome(),
              config.flinkCdcHome(),
              config.javaHome(),
              config.flinkVersion(),
              config.flinkCdcVersion(),
              fallback);
      store.update(
          environment.id(),
          environment.name(),
          environment.submitterType(),
          migrated,
          environment.enabled());
    }
  }

  private void backfillLegacyRuntimeBindings() {
    ComputeEnvironment environment = store.defaultEnvironment().orElse(null);
    if (environment == null) {
      return;
    }
    transactions.executeWithoutResult(status -> store.bindLegacyRealtimeJobs(environment));
  }

  private ComputeEnvironment require(long id) {
    return store.find(id).orElseThrow(() -> new IllegalArgumentException("运行环境不存在：" + id));
  }

  private String normalizeName(String value) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException("运行环境名称不能为空");
    }
    String normalized = value.trim();
    if (normalized.length() > 120) {
      throw new IllegalArgumentException("运行环境名称不能超过 120 个字符");
    }
    return normalized;
  }

  private String normalizeSubmitterType(String value, String fallback) {
    String normalized =
        StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : fallback;
    if (!ComputeEnvironment.SUBMITTER_LOCAL.equals(normalized)
        && !ComputeEnvironment.SUBMITTER_SSH.equals(normalized)) {
      throw new IllegalArgumentException("不支持的任务提交方式：" + value);
    }
    return normalized;
  }

  private RuntimeConfig preserveExistingSshConfig(
      ComputeEnvironment current, String submitterType, RuntimeConfig requested) {
    if (requested == null
        || !ComputeEnvironment.SUBMITTER_SSH.equals(submitterType)
        || requested.ssh() != null
        || current.config() == null
        || current.config().ssh() == null) {
      return requested;
    }
    return new RuntimeConfig(
        requested.restUrl(),
        requested.flinkHome(),
        requested.flinkCdcHome(),
        requested.javaHome(),
        requested.flinkVersion(),
        requested.flinkCdcVersion(),
        current.config().ssh());
  }

  private RuntimeConfig normalizeConfig(RuntimeConfig value, String submitterType) {
    if (value == null) {
      throw new IllegalArgumentException("运行环境配置不能为空");
    }
    String restUrl = required(value.restUrl(), "Flink REST URL", 500);
    validateRestUrl(restUrl);
    String flinkHome = required(value.flinkHome(), "Flink Home", 500);
    String flinkCdcHome = required(value.flinkCdcHome(), "Flink CDC Home", 500);
    String javaHome = optional(value.javaHome(), "Java Home", 500);
    String flinkVersion = required(value.flinkVersion(), "Flink 版本", 64);
    String flinkCdcVersion = required(value.flinkCdcVersion(), "Flink CDC 版本", 64);
    SshConfig ssh = null;
    if (ComputeEnvironment.SUBMITTER_SSH.equals(submitterType)) {
      if (!absoluteUnixPath(flinkHome) || !absoluteUnixPath(flinkCdcHome)) {
        throw new IllegalArgumentException("SSH 远程执行时 Flink Home 和 Flink CDC Home 必须是 Linux 绝对路径");
      }
      if (StringUtils.hasText(javaHome) && !absoluteUnixPath(javaHome)) {
        throw new IllegalArgumentException("SSH 远程执行时 Java Home 必须是 Linux 绝对路径");
      }
      ssh = normalizeSshConfig(value.ssh());
    }
    return new RuntimeConfig(
        restUrl, flinkHome, flinkCdcHome, javaHome, flinkVersion, flinkCdcVersion, ssh);
  }

  private SshConfig normalizeSshConfig(SshConfig value) {
    if (value == null) {
      throw new IllegalArgumentException("请选择 SSH 远程执行并填写提交节点配置");
    }
    String executable =
        StringUtils.hasText(value.executable()) ? value.executable().trim() : "ssh";
    if (executable.length() > 500) {
      throw new IllegalArgumentException("SSH executable 长度不能超过 500 个字符");
    }
    String host = required(value.host(), "SSH Host", 255);
    if (!SSH_HOST.matcher(host).matches()) {
      throw new IllegalArgumentException("SSH Host 格式无效");
    }
    int port = value.port() == null ? 22 : value.port();
    requirePort(port, "SSH Port");
    String user = required(value.user(), "SSH User", 128);
    if (!SSH_USER.matcher(user).matches()) {
      throw new IllegalArgumentException("SSH User 格式无效");
    }
    String identityFile = optional(value.identityFile(), "SSH Identity File", 500);
    String knownHostsFile = optional(value.knownHostsFile(), "SSH Known Hosts File", 500);
    boolean strictHostKeyChecking =
        value.strictHostKeyChecking() == null || value.strictHostKeyChecking();
    int connectTimeoutSeconds =
        value.connectTimeoutSeconds() == null ? 5 : value.connectTimeoutSeconds();
    if (connectTimeoutSeconds < 1 || connectTimeoutSeconds > 120) {
      throw new IllegalArgumentException("SSH 连接超时必须在 1-120 秒之间");
    }
    String remoteRestAddress = optional(value.remoteRestAddress(), "远端 Flink REST 地址", 255);
    if (StringUtils.hasText(remoteRestAddress) && !SSH_HOST.matcher(remoteRestAddress).matches()) {
      throw new IllegalArgumentException("远端 Flink REST 地址格式无效");
    }
    Integer remoteRestPort = value.remoteRestPort();
    if (remoteRestPort != null) {
      requirePort(remoteRestPort, "远端 Flink REST Port");
    }
    return new SshConfig(
        executable,
        host,
        port,
        user,
        identityFile,
        knownHostsFile,
        strictHostKeyChecking,
        connectTimeoutSeconds,
        remoteRestAddress,
        remoteRestPort);
  }

  private SshConfig bootstrapSshConfig() {
    RealtimeSyncProperties.Ssh ssh = properties.getSsh();
    Duration timeout = ssh.getConnectTimeout();
    int timeoutSeconds =
        timeout == null ? 5 : (int) Math.max(1, Math.min(120, timeout.toSeconds()));
    return new SshConfig(
        ssh.getExecutable(),
        ssh.getHost(),
        ssh.getPort(),
        ssh.getUser(),
        ssh.getIdentityFile(),
        ssh.getKnownHostsFile(),
        ssh.isStrictHostKeyChecking(),
        timeoutSeconds,
        ssh.getRemoteRestAddress(),
        ssh.getRemoteRestPort());
  }

  private void validateRestUrl(String value) {
    try {
      URI uri = URI.create(value);
      String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
      if (!("http".equals(scheme) || "https".equals(scheme)) || !StringUtils.hasText(uri.getHost())) {
        throw new IllegalArgumentException("Flink REST URL 必须是完整的 http/https 地址");
      }
    } catch (IllegalArgumentException exception) {
      if (exception.getMessage() != null && exception.getMessage().startsWith("Flink REST URL")) {
        throw exception;
      }
      throw new IllegalArgumentException("Flink REST URL 格式无效", exception);
    }
  }

  private String required(String value, String label, int maxLength) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException(label + "不能为空");
    }
    String normalized = value.trim();
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException(label + "长度不能超过 " + maxLength + " 个字符");
    }
    return normalized;
  }

  private String optional(String value, String label, int maxLength) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    String normalized = value.trim();
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException(label + "长度不能超过 " + maxLength + " 个字符");
    }
    return normalized;
  }

  private void requirePort(int port, String label) {
    if (port < 1 || port > 65535) {
      throw new IllegalArgumentException(label + " 必须在 1-65535 之间");
    }
  }

  private boolean absoluteUnixPath(String value) {
    return StringUtils.hasText(value) && value.startsWith("/");
  }

  private SubmissionMode submissionMode(String value) {
    try {
      return SubmissionMode.valueOf(value);
    } catch (Exception exception) {
      throw new IllegalStateException("不支持的任务提交方式：" + value, exception);
    }
  }
}
