package io.yak.ops.boot.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.framework.security.common.dto.message.MessageDTO;
import io.yak.framework.security.common.enums.project.ProjectUserCode;
import io.yak.framework.security.notification.NotificationPublisher;
import io.yak.framework.security.service.UserProjectService;
import io.yak.ops.core.notification.BusinessNotification;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class YakSecurityBusinessNotificationGatewayTest {

  @AfterEach
  void clearTransactionSynchronization() {
    TransactionSynchronizationManager.setActualTransactionActive(false);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void sendsOneProjectOwnedMessageToEachDistinctOwner() {
    UserProjectService projects = mock(UserProjectService.class);
    NotificationPublisher publisher = mock(NotificationPublisher.class);
    ObjectProvider<UserProjectService> projectProvider = mock(ObjectProvider.class);
    ObjectProvider<NotificationPublisher> publisherProvider = mock(ObjectProvider.class);
    when(projectProvider.getIfAvailable()).thenReturn(projects);
    when(publisherProvider.getIfAvailable()).thenReturn(publisher);
    when(projects.getUserIdListByProjectId(7L, ProjectUserCode.OWNER))
        .thenReturn(List.of(11L, 12L, 11L));

    YakSecurityBusinessNotificationGateway gateway =
        new YakSecurityBusinessNotificationGateway(projectProvider, publisherProvider);
    gateway.publishToProjectOwners(notification());

    ArgumentCaptor<List<MessageDTO>> captor = ArgumentCaptor.forClass(List.class);
    verify(publisher).publishAll(captor.capture());
    assertThat(captor.getValue()).hasSize(2);
    assertThat(captor.getValue()).extracting(MessageDTO::getUserId)
        .containsExactly(11L, 12L);
    assertThat(captor.getValue()).allSatisfy(message -> {
      assertThat(message.getProjectId()).isEqualTo(7L);
      assertThat(message.getType()).isEqualTo("TASK");
      assertThat(message.getLevel()).isEqualTo("ERROR");
      assertThat(message.getSourceType()).isEqualTo("OFFLINE_SYNC_EXECUTION");
      assertThat(message.getActionPath()).isEqualTo("/sync/batch-link-up/10/detail");
    });
  }

  @Test
  @SuppressWarnings("unchecked")
  void defersDeliveryUntilTheBusinessTransactionCommits() {
    UserProjectService projects = mock(UserProjectService.class);
    NotificationPublisher publisher = mock(NotificationPublisher.class);
    ObjectProvider<UserProjectService> projectProvider = mock(ObjectProvider.class);
    ObjectProvider<NotificationPublisher> publisherProvider = mock(ObjectProvider.class);
    when(projectProvider.getIfAvailable()).thenReturn(projects);
    when(publisherProvider.getIfAvailable()).thenReturn(publisher);
    when(projects.getUserIdListByProjectId(7L, ProjectUserCode.OWNER))
        .thenReturn(List.of(11L));

    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);

    YakSecurityBusinessNotificationGateway gateway =
        new YakSecurityBusinessNotificationGateway(projectProvider, publisherProvider);
    gateway.publishToProjectOwners(notification());

    verify(publisher, never()).publishAll(anyList());
    List<TransactionSynchronization> synchronizations =
        TransactionSynchronizationManager.getSynchronizations();
    assertThat(synchronizations).hasSize(1);

    synchronizations.get(0).afterCommit();

    verify(publisher).publishAll(anyList());
  }

  @Test
  @SuppressWarnings("unchecked")
  void deliveryFailureNeverEscapesIntoTheBusinessFlow() {
    UserProjectService projects = mock(UserProjectService.class);
    NotificationPublisher publisher = mock(NotificationPublisher.class);
    ObjectProvider<UserProjectService> projectProvider = mock(ObjectProvider.class);
    ObjectProvider<NotificationPublisher> publisherProvider = mock(ObjectProvider.class);
    when(projectProvider.getIfAvailable()).thenReturn(projects);
    when(publisherProvider.getIfAvailable()).thenReturn(publisher);
    when(projects.getUserIdListByProjectId(7L, ProjectUserCode.OWNER))
        .thenReturn(List.of(11L));
    doThrow(new IllegalStateException("message store unavailable"))
        .when(publisher).publishAll(anyList());

    YakSecurityBusinessNotificationGateway gateway =
        new YakSecurityBusinessNotificationGateway(projectProvider, publisherProvider);

    assertThatCode(() -> gateway.publishToProjectOwners(notification()))
        .doesNotThrowAnyException();
  }

  private BusinessNotification notification() {
    return new BusinessNotification(
        7L,
        BusinessNotification.Type.TASK,
        BusinessNotification.Level.ERROR,
        "离线同步任务执行失败",
        "订单同步",
        "engine down",
        "OFFLINE_SYNC_EXECUTION",
        "99",
        "/sync/batch-link-up/10/detail");
  }
}
