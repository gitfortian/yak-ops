package io.yak.ops.business.sync.offline.domain;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Yak Ops 对 Link-Up 离线 Attempt 状态的稳定映射。
 *
 * <p>LOST 只作为旧持久化值读取，并统一归一为 UNKNOWN；它不再是新的领域状态。
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
  UNKNOWN;

  private static final Set<OfflineExecutionStatus> ACTIVE =
      EnumSet.of(CREATED, SUBMITTED, QUEUED, RUNNING, UNKNOWN);

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
