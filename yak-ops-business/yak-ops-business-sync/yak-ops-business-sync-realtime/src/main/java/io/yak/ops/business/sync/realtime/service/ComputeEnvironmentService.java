package io.yak.ops.business.sync.realtime.service;

import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties;
import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties.RuntimeOverrides;
import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties.SubmissionMode;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.repository.ComputeEnvironmentStore;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/** Manages the small, user-facing runtime environment model for realtime Flink CDC. */
@Service
@DependsOn("realtimeSyncFlyway")
public class ComputeEnvironmentService {

  private static final String BOOTSTRAP_NAME = "默认实时环境";

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
    refreshRuntimeOverrides();
  }

  public List<ComputeEnvironment> list() {
    return store.list();
  }

  public ComputeEnvironment get(long id) {
    return require(id);
  }

  public long create(String name, RuntimeConfig config, boolean enabled, boolean makeDefault) {
    String normalizedName = normalizeName(name);
    RuntimeConfig normalizedConfig = normalizeConfig(config);
    if (makeDefault && !enabled) {
      throw new IllegalArgumentException("默认运行环境必须保持启用");
    }
    if (makeDefault) {
      requireRuntimeStable("切换默认运行环境");
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
                    ComputeEnvironment.SUBMITTER_LOCAL,
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

  public void update(long id, String name, RuntimeConfig config, boolean enabled) {
    ComputeEnvironment current = require(id);
    String normalizedName = normalizeName(name);
    RuntimeConfig normalizedConfig = normalizeConfig(config);

    if (current.defaultEnvironment() && !enabled) {
      throw new IllegalStateException("默认运行环境不能停用，请先切换默认环境");
    }
    if (current.defaultEnvironment() && !current.config().equals(normalizedConfig)) {
      requireRuntimeStable("修改默认运行环境");
    }

    try {
      store.update(id, normalizedName, normalizedConfig, enabled);
    } catch (DuplicateKeyException exception) {
      throw new IllegalArgumentException("运行环境名称已存在：" + normalizedName, exception);
    }
    if (current.defaultEnvironment()) {
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
    requireRuntimeStable("切换默认运行环境");
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
    store.delete(id);
  }

  /**
   * Keeps every Yak Ops instance aligned with the database-selected default environment. Runtime
   * environments cannot be changed while jobs are active, so the refresh is safe for running jobs.
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
    RuntimeConfig config =
        new RuntimeConfig(
            properties.getRestUrl(),
            properties.getFlinkHome(),
            properties.getFlinkCdcHome(),
            properties.getJavaHome(),
            properties.getFlinkVersion(),
            properties.getFlinkCdcVersion());
    try {
      transactions.executeWithoutResult(
          status -> {
            if (store.count() == 0) {
              store.insert(
                  BOOTSTRAP_NAME,
                  ComputeEnvironment.ENGINE_FLINK_CDC,
                  ComputeEnvironment.DEPLOYMENT_REMOTE,
                  properties.getSubmissionMode().name(),
                  normalizeConfig(config),
                  true,
                  true);
            }
          });
    } catch (DuplicateKeyException ignored) {
      // Another Yak Ops instance won the bootstrap race.
    }
  }

  private ComputeEnvironment require(long id) {
    return store.find(id).orElseThrow(() -> new IllegalArgumentException("运行环境不存在：" + id));
  }

  private void requireRuntimeStable(String action) {
    if (store.hasActiveRealtimeJobs()) {
      throw new IllegalStateException(action + "前请先停止所有实时同步任务");
    }
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

  private RuntimeConfig normalizeConfig(RuntimeConfig value) {
    if (value == null) {
      throw new IllegalArgumentException("运行环境配置不能为空");
    }
    String restUrl = required(value.restUrl(), "Flink REST URL", 500);
    validateRestUrl(restUrl);
    String flinkHome = required(value.flinkHome(), "Flink Home", 500);
    String flinkCdcHome = required(value.flinkCdcHome(), "Flink CDC Home", 500);
    String javaHome = optional(value.javaHome(), 500);
    String flinkVersion = required(value.flinkVersion(), "Flink 版本", 64);
    String flinkCdcVersion = required(value.flinkCdcVersion(), "Flink CDC 版本", 64);
    return new RuntimeConfig(
        restUrl, flinkHome, flinkCdcHome, javaHome, flinkVersion, flinkCdcVersion);
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

  private String optional(String value, int maxLength) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    String normalized = value.trim();
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException("Java Home 长度不能超过 " + maxLength + " 个字符");
    }
    return normalized;
  }

  private SubmissionMode submissionMode(String value) {
    try {
      return SubmissionMode.valueOf(value);
    } catch (Exception exception) {
      throw new IllegalStateException("不支持的任务提交方式：" + value, exception);
    }
  }
}
