package io.yak.ops.business.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditTimelineRendererRegistryTest {

  private final AuditTimelineRendererRegistry registry = new AuditTimelineRendererRegistry();

  @Test
  void authorizationDecisionUsesBusinessCopyAndStableReasonLabel() {
    AuditEventPresentation presentation =
        registry.render(
            "AUTHORIZATION_DECISION",
            "FAILURE",
            "PROJECT_MEMBERSHIP_REQUIRED",
            "Authorization denied",
            Map.of("permission", "PROJECT_ACCESS", "decision", "DENY"));

    assertThat(presentation.title()).isEqualTo("权限检查拒绝");
    assertThat(presentation.description()).contains("项目空间访问").contains("当前用户不是项目成员");
  }

  @Test
  void knownBusinessEventUsesRegistryWithoutLeakingTechnicalTypeAsTitle() {
    AuditEventPresentation presentation =
        registry.render("TASK_QUEUED", "INFO", null, "Queued", Map.of());

    assertThat(presentation.title()).isEqualTo("任务已进入队列");
    assertThat(presentation.description()).isEqualTo("Queued");
  }

  @Test
  void resourceUpdateUsesStableBusinessChangeTypeWhenAvailable() {
    assertThat(
            registry
                .render(
                    "RESOURCE_UPDATED",
                    "SUCCESS",
                    null,
                    "Workflow version published",
                    Map.of("changeType", "VERSION_PUBLISHED"))
                .title())
        .isEqualTo("版本已发布");
    assertThat(
            registry
                .render(
                    "RESOURCE_UPDATED",
                    "SUCCESS",
                    null,
                    "Workflow enabled",
                    Map.of("changeType", "RESOURCE_ENABLED"))
                .title())
        .isEqualTo("资源已启用");
    assertThat(
            registry
                .render(
                    "RESOURCE_UPDATED",
                    "SUCCESS",
                    null,
                    "Workflow disabled",
                    Map.of("changeType", "RESOURCE_DISABLED"))
                .title())
        .isEqualTo("资源已停用");
  }

  @Test
  void unknownEventHasSafeFallback() {
    AuditEventPresentation presentation =
        registry.render("CUSTOM_EVENT", "INFO", null, null, Map.of());

    assertThat(presentation.title()).isEqualTo("custom event");
  }
}
