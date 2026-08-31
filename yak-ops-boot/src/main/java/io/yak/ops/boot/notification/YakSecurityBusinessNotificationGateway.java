package io.yak.ops.boot.notification;

import io.yak.framework.security.common.dto.message.MessageDTO;
import io.yak.framework.security.common.enums.project.ProjectUserCode;
import io.yak.framework.security.notification.NotificationPublisher;
import io.yak.framework.security.service.UserProjectService;
import io.yak.ops.core.notification.BusinessNotification;
import io.yak.ops.core.notification.BusinessNotificationGateway;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Yak Ops business-notification adapter backed by Yak Security's Message Center. */
@Component
public class YakSecurityBusinessNotificationGateway implements BusinessNotificationGateway {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(YakSecurityBusinessNotificationGateway.class);

  private final ObjectProvider<UserProjectService> userProjectServices;
  private final ObjectProvider<NotificationPublisher> notificationPublishers;

  public YakSecurityBusinessNotificationGateway(
      ObjectProvider<UserProjectService> userProjectServices,
      ObjectProvider<NotificationPublisher> notificationPublishers) {
    this.userProjectServices = userProjectServices;
    this.notificationPublishers = notificationPublishers;
  }

  @Override
  public void publishToProjectOwners(BusinessNotification notification) {
    Objects.requireNonNull(notification, "notification");
    try {
      Runnable delivery = () -> publishNow(notification);
      if (TransactionSynchronizationManager.isSynchronizationActive()
          && TransactionSynchronizationManager.isActualTransactionActive()) {
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
              @Override
              public void afterCommit() {
                delivery.run();
              }
            });
        return;
      }
      delivery.run();
    } catch (RuntimeException exception) {
      // Even transaction-synchronization failures are secondary notification failures.
      logFailure(notification, exception);
    }
  }

  private void publishNow(BusinessNotification notification) {
    try {
      UserProjectService userProjectService = userProjectServices.getIfAvailable();
      NotificationPublisher notificationPublisher = notificationPublishers.getIfAvailable();
      if (userProjectService == null || notificationPublisher == null) {
        LOGGER.debug(
            "Business notification skipped because Yak Security notification infrastructure is unavailable: sourceType={}, sourceId={}",
            notification.sourceType(),
            notification.sourceId());
        return;
      }

      List<Long> resolvedOwnerIds =
          userProjectService.getUserIdListByProjectId(
              notification.projectId(), ProjectUserCode.OWNER);
      List<Long> ownerIds = resolvedOwnerIds == null
          ? List.of()
          : resolvedOwnerIds.stream()
              .filter(Objects::nonNull)
              .filter(userId -> userId > 0L)
              .distinct()
              .toList();
      if (ownerIds.isEmpty()) {
        LOGGER.debug(
            "Business notification skipped because Project has no owner: projectId={}, sourceType={}, sourceId={}",
            notification.projectId(),
            notification.sourceType(),
            notification.sourceId());
        return;
      }

      notificationPublisher.publishAll(
          ownerIds.stream().map(userId -> toMessage(notification, userId)).toList());
    } catch (RuntimeException exception) {
      // Notification delivery is deliberately best-effort. It must never turn a successful
      // business state transition into a failed sync/workflow/quality execution.
      logFailure(notification, exception);
    }
  }

  private void logFailure(
      BusinessNotification notification,
      RuntimeException exception) {
    LOGGER.error(
        "Business notification delivery failed: projectId={}, sourceType={}, sourceId={}",
        notification.projectId(),
        notification.sourceType(),
        notification.sourceId(),
        exception);
  }

  private MessageDTO toMessage(BusinessNotification notification, Long userId) {
    MessageDTO message = new MessageDTO();
    message.setUserId(userId);
    message.setProjectId(notification.projectId());
    message.setType(notification.type().name());
    message.setLevel(notification.level().name());
    message.setTitle(notification.title());
    message.setSummary(notification.summary());
    message.setContent(notification.content());
    message.setSourceType(notification.sourceType());
    message.setSourceId(notification.sourceId());
    message.setActionPath(notification.actionPath());
    return message;
  }
}
