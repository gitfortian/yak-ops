package io.yak.ops.core.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BusinessNotificationTest {

  @Test
  void normalizesCopyAndAcceptsOnlyInternalActions() {
    BusinessNotification notification = new BusinessNotification(
        7L,
        BusinessNotification.Type.TASK,
        BusinessNotification.Level.ERROR,
        "  Task failed  ",
        "  summary  ",
        "  detail  ",
        "  OFFLINE_SYNC_EXECUTION  ",
        "  99  ",
        "  /sync/batch-link-up/10/detail  ");

    assertThat(notification.title()).isEqualTo("Task failed");
    assertThat(notification.summary()).isEqualTo("summary");
    assertThat(notification.sourceType()).isEqualTo("OFFLINE_SYNC_EXECUTION");
    assertThat(notification.sourceId()).isEqualTo("99");
    assertThat(notification.actionPath()).isEqualTo("/sync/batch-link-up/10/detail");
  }

  @Test
  void rejectsInvalidProjectAndExternalActions() {
    assertThatThrownBy(() -> new BusinessNotification(
        0L,
        BusinessNotification.Type.TASK,
        BusinessNotification.Level.ERROR,
        "failed",
        null,
        null,
        "TASK",
        "1",
        "/tasks/1"))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> new BusinessNotification(
        7L,
        BusinessNotification.Type.TASK,
        BusinessNotification.Level.ERROR,
        "failed",
        null,
        null,
        "TASK",
        "1",
        "https://example.com"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
