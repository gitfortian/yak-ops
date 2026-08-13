package io.yak.ops.business.workflow.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 工作流启动上下文。
 *
 * <p>Definition、调度、补数和 API 最终都通过同一个 Launch 入口进入 Runtime，
 * 该上下文只描述“为什么启动”，不参与 DAG 本身的编排语义。</p>
 */
public record WorkflowTriggerContext(
    WorkflowTriggerType triggerType,
    String triggerId,
    String scheduleId,
    Instant plannedFireTime) {

  public WorkflowTriggerContext {
    if (triggerType == null) {
      throw new IllegalArgumentException("工作流 triggerType 不能为空");
    }
    triggerId = required(triggerId, "工作流 triggerId 不能为空");
    scheduleId = trimToNull(scheduleId);
  }

  public static WorkflowTriggerContext manual() {
    return new WorkflowTriggerContext(
        WorkflowTriggerType.MANUAL,
        generatedId("manual"),
        null,
        null);
  }

  public static WorkflowTriggerContext api() {
    return new WorkflowTriggerContext(
        WorkflowTriggerType.API,
        generatedId("api"),
        null,
        null);
  }

  public static WorkflowTriggerContext scheduled(
      String triggerId,
      String scheduleId,
      Instant plannedFireTime) {
    String safeScheduleId = required(scheduleId, "工作流 scheduleId 不能为空");
    if (plannedFireTime == null) {
      throw new IllegalArgumentException("调度 plannedFireTime 不能为空");
    }
    return new WorkflowTriggerContext(
        WorkflowTriggerType.SCHEDULE,
        triggerId,
        safeScheduleId,
        plannedFireTime);
  }

  public static WorkflowTriggerContext backfill(
      String triggerId,
      String scheduleId,
      Instant plannedFireTime) {
    if (plannedFireTime == null) {
      throw new IllegalArgumentException("补数 plannedFireTime 不能为空");
    }
    return new WorkflowTriggerContext(
        WorkflowTriggerType.BACKFILL,
        triggerId,
        scheduleId,
        plannedFireTime);
  }

  private static String generatedId(String prefix) {
    return "workflow-trigger-" + prefix + "-" + UUID.randomUUID();
  }

  private static String required(String value, String message) {
    String normalized = trimToNull(value);
    if (normalized == null) throw new IllegalArgumentException(message);
    return normalized;
  }

  private static String trimToNull(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
