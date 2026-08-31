package io.yak.ops.business.quality.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.quality.domain.execution.QualityExecutionPlan;
import io.yak.ops.business.quality.domain.execution.QualityExecutionPlan.MonitorSnapshot;
import io.yak.ops.business.quality.repository.QualityAlertRepository;
import io.yak.ops.common.enums.quality.QualityEnums.AlertLevel;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.NotifyChannel;
import io.yak.ops.common.enums.quality.QualityEnums.RuleFailureAction;
import io.yak.ops.core.notification.BusinessNotification;
import io.yak.ops.core.notification.BusinessNotificationGateway;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class QualityAlertRecorderNotificationTest {

  @Test
  @SuppressWarnings("unchecked")
  void messageChannelPublishesQualityWarningToProjectOwners() {
    QualityAlertRepository repository = mock(QualityAlertRepository.class);
    BusinessNotificationGateway gateway = mock(BusinessNotificationGateway.class);
    ObjectProvider<BusinessNotificationGateway> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(gateway);

    QualityAlertRecorder recorder = new QualityAlertRecorder(repository, provider);
    recorder.recordIfNecessary(plan(NotifyChannel.MESSAGE), CheckResult.NOT_PASSED, 8, 2, 0);

    verify(repository).insertAlertEvent(any());
    ArgumentCaptor<BusinessNotification> captor =
        ArgumentCaptor.forClass(BusinessNotification.class);
    verify(gateway).publishToProjectOwners(captor.capture());
    BusinessNotification notification = captor.getValue();
    assertThat(notification.projectId()).isEqualTo(7L);
    assertThat(notification.type()).isEqualTo(BusinessNotification.Type.QUALITY);
    assertThat(notification.level()).isEqualTo(BusinessNotification.Level.WARNING);
    assertThat(notification.title()).isEqualTo("数据质量检查发现问题");
    assertThat(notification.sourceType()).isEqualTo("DATA_QUALITY_EXECUTION");
    assertThat(notification.sourceId()).isEqualTo("Q-20260831-001");
    assertThat(notification.actionPath())
        .isEqualTo("/data-quality/execution/Q-20260831-001");
  }

  @Test
  @SuppressWarnings("unchecked")
  void nonMessageChannelKeepsAlertEvidenceWithoutMessageCenterDelivery() {
    QualityAlertRepository repository = mock(QualityAlertRepository.class);
    BusinessNotificationGateway gateway = mock(BusinessNotificationGateway.class);
    ObjectProvider<BusinessNotificationGateway> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(gateway);

    QualityAlertRecorder recorder = new QualityAlertRecorder(repository, provider);
    recorder.recordIfNecessary(plan(NotifyChannel.EMAIL), CheckResult.ERROR, 0, 0, 1);

    verify(repository).insertAlertEvent(any());
    verify(gateway, never()).publishToProjectOwners(any());
  }

  private QualityExecutionPlan plan(NotifyChannel channel) {
    return new QualityExecutionPlan(
        7L,
        101L,
        "Q-20260831-001",
        new MonitorSnapshot(
            21L,
            "订单完整性检查",
            31L,
            "warehouse",
            "dwd",
            "public",
            "dwd_order",
            null,
            "alice"),
        List.of(),
        RuleFailureAction.CONTINUE,
        true,
        channel,
        null,
        AlertLevel.WARNING);
  }
}
