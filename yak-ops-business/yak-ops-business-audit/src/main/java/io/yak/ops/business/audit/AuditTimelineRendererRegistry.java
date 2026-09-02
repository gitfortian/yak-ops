package io.yak.ops.business.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Maps stable event vocabulary to business-facing Timeline copy.
 *
 * <p>Keeping renderers in a registry prevents Audit Center UI from growing a giant event-type
 * switch.
 */
public final class AuditTimelineRendererRegistry {

  private static final Map<String, String> REASON_LABELS = reasonLabels();
  private static final Map<String, String> PERMISSION_LABELS = permissionLabels();

  private final Map<String, Function<RenderContext, AuditEventPresentation>> renderers;

  public AuditTimelineRendererRegistry() {
    Map<String, Function<RenderContext, AuditEventPresentation>> values = new LinkedHashMap<>();
    values.put("AUTHORIZATION_DECISION", this::authorization);
    values.put("OPERATION_STARTED", fixed("操作开始"));
    values.put("RESOURCE_CREATED", fixed("资源已创建"));
    values.put("RESOURCE_UPDATED", fixed("资源已更新"));
    values.put("RESOURCE_DELETED", fixed("资源已删除"));
    values.put("TASK_SUBMITTED", fixed("任务已提交"));
    values.put("TASK_QUEUED", fixed("任务已进入队列"));
    values.put("WORKER_STARTED", fixed("执行器开始处理"));
    values.put("TASK_SUCCEEDED", fixed("任务执行成功"));
    values.put("TASK_FAILED", fixed("任务执行失败"));
    values.put("TASK_CANCELED", fixed("任务已取消"));
    values.put("OPERATION_SUCCEEDED", fixed("操作完成"));
    values.put("OPERATION_FAILED", fixed("操作失败"));
    this.renderers = Map.copyOf(values);
  }

  public AuditEventPresentation render(
      String eventType,
      String eventStatus,
      String reasonCode,
      String message,
      Map<String, Object> payload) {
    RenderContext context =
        new RenderContext(
            eventType,
            eventStatus,
            reasonCode,
            message,
            payload == null ? Map.of() : payload);
    Function<RenderContext, AuditEventPresentation> renderer = renderers.get(eventType);
    if (renderer != null) return renderer.apply(context);
    return new AuditEventPresentation(humanize(eventType), description(context));
  }

  private Function<RenderContext, AuditEventPresentation> fixed(String title) {
    return context -> new AuditEventPresentation(title, description(context));
  }

  private AuditEventPresentation authorization(RenderContext context) {
    String decision = stringValue(context.payload().get("decision"));
    boolean denied =
        "DENY".equalsIgnoreCase(decision)
            || "FAILURE".equalsIgnoreCase(context.eventStatus());
    String permission = permissionLabel(stringValue(context.payload().get("permission")));
    String reason = reasonLabel(context.reasonCode());
    String detail =
        permission == null ? reason : permission + (reason == null ? "" : " · " + reason);
    return new AuditEventPresentation(denied ? "权限检查拒绝" : "权限检查通过", detail);
  }

  private String description(RenderContext context) {
    String reason = reasonLabel(context.reasonCode());
    if (reason != null) return reason;
    if (context.message() != null && !context.message().isBlank()) return context.message();
    return null;
  }

  private String permissionLabel(String permission) {
    if (permission == null || permission.isBlank()) return null;
    return PERMISSION_LABELS.getOrDefault(permission, permission);
  }

  private String reasonLabel(String reasonCode) {
    if (reasonCode == null || reasonCode.isBlank()) return null;
    return REASON_LABELS.getOrDefault(reasonCode, reasonCode);
  }

  private String humanize(String value) {
    if (value == null || value.isBlank()) return "审计事件";
    return value.toLowerCase().replace('_', ' ');
  }

  private String stringValue(Object value) {
    if (value == null) return null;
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? null : text;
  }

  private static Map<String, String> permissionLabels() {
    return Map.of("PROJECT_ACCESS", "项目空间访问");
  }

  private static Map<String, String> reasonLabels() {
    Map<String, String> labels = new LinkedHashMap<>();
    labels.put("PROJECT_REQUIRED", "需要选择项目空间");
    labels.put("PROJECT_ID_INVALID", "项目空间标识无效");
    labels.put("PROJECT_ACCESS_INPUT_INVALID", "项目访问上下文无效");
    labels.put("PROJECT_NOT_FOUND", "项目空间不存在或不可访问");
    labels.put("PROJECT_ACTOR_NOT_FOUND", "未找到有效的访问主体");
    labels.put("PROJECT_MEMBERSHIP_REQUIRED", "当前用户不是项目成员");
    labels.put("PROJECT_UNAVAILABLE", "项目空间当前不可用");
    labels.put("PROJECT_OWNER_ACCESS_ALLOWED", "项目所有者权限通过");
    labels.put("PROJECT_MEMBER_ACCESS_ALLOWED", "项目成员权限通过");
    return Map.copyOf(labels);
  }

  private record RenderContext(
      String eventType,
      String eventStatus,
      String reasonCode,
      String message,
      Map<String, Object> payload) {}
}
