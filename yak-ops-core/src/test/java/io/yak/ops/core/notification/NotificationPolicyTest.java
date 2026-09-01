package io.yak.ops.core.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NotificationPolicyTest {

  @Test
  void defaultPolicyPreservesProjectOwnerInAppBehavior() {
    NotificationPolicy policy = NotificationPolicy.projectOwnersInApp();

    assertThat(policy.enabled()).isTrue();
    assertThat(policy.recipientStrategy())
        .isEqualTo(NotificationPolicy.RecipientStrategy.PROJECT_OWNER);
    assertThat(policy.routesTo(NotificationPolicy.Destination.IN_APP)).isTrue();
    assertThat(policy.routesTo(NotificationPolicy.Destination.ALERT)).isFalse();
    assertThat(policy.alertChannelIds()).isEmpty();
  }

  @Test
  void normalizesAlertChannelIds() {
    NotificationPolicy policy = new NotificationPolicy(
        true,
        NotificationPolicy.RecipientStrategy.PROJECT_OWNER,
        Set.of(NotificationPolicy.Destination.ALERT),
        java.util.Arrays.asList(3L, null, -1L, 3L, 5L));

    assertThat(policy.alertChannelIds()).containsExactly(3L, 5L);
  }

  @Test
  void enabledPolicyRequiresDestination() {
    assertThatThrownBy(() -> new NotificationPolicy(
        true,
        NotificationPolicy.RecipientStrategy.PROJECT_OWNER,
        Set.of(),
        List.of()))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(NotificationPolicy.disabled().enabled()).isFalse();
  }
}
