package io.yak.ops.business.sync.offline.domain;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Yak Ops 对 Link-Up 离线作业状态的稳定映射。
 *
 * @author weifuwan
 */
public enum OfflineExecutionStatus {
  CREATED,
  SUBMITTED,
  QUEUED,
  RUNNING,
  SUCCEEDED,
  FAILED,
  CANCELED,
  UNKNOWN,
  /** Wave 3 仅保留旧数据兼容；读取 LOST 时统一解释为 UNKNOWN。 */
  LOST;

  private static final Set<OfflineExecutionStatus> ACTIVE =
      EnumSet.of(CREATED, SUBMITTED, QUEUED, RUNNING, UNKNOWN, LOST);

  public boolean isActive() {
    return ACTIVE.contains(this);
  }

  public boolean isTerminal() {
    return !isActive();
  }

  public static OfflineExecutionStatus parse(String value) {
    if (value == null || value.trim().isEmpty()) {
      return CREATED;
    }
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if ("FINISHED".equals(normalized) || "COMPLETED".equals(normalized)) {
      return SUCCEEDED;
    }
    if ("CANCELLED".equals(normalized) || "CANCELING".equals(normalized)
        || "CANCELLING".equals(normalized)) {
      return CANCELED;
    }
    if ("LOST".equals(normalized)) {
      return UNKNOWN;
    }
    return valueOf(normalized);
  }

  public static boolean isActive(String value) {
    if (value == null || value.trim().isEmpty()) {
      return false;
    }
    try {
      return parse(value).isActive();
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }
}
