package io.yak.ops.core.notification;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Effective routing policy for one notification intent.
 *
 * <p>Business modules do not interpret this policy directly. Resolvers choose an effective policy
 * and sinks consume only the parts relevant to their delivery destination.</p>
 */
public record NotificationPolicy(
    boolean enabled,
    RecipientStrategy recipientStrategy,
    Set<Destination> destinations,
    List<Long> alertChannelIds) {

  public NotificationPolicy {
    if (recipientStrategy == null) {
      throw new IllegalArgumentException("notification recipientStrategy must not be null");
    }
    destinations = destinations == null ? Set.of() : Set.copyOf(destinations);
    alertChannelIds = normalizeIds(alertChannelIds);
    if (enabled && destinations.isEmpty()) {
      throw new IllegalArgumentException("enabled notification policy requires a destination");
    }
  }

  /** Current backward-compatible default inherited from the original Message Center integration. */
  public static NotificationPolicy projectOwnersInApp() {
    return new NotificationPolicy(
        true,
        RecipientStrategy.PROJECT_OWNER,
        Set.of(Destination.IN_APP),
        List.of());
  }

  public static NotificationPolicy disabled() {
    return new NotificationPolicy(
        false,
        RecipientStrategy.PROJECT_OWNER,
        Set.of(),
        List.of());
  }

  public boolean routesTo(Destination destination) {
    return enabled && destination != null && destinations.contains(destination);
  }

  public enum RecipientStrategy {
    PROJECT_OWNER
  }

  public enum Destination {
    IN_APP,
    ALERT
  }

  private static List<Long> normalizeIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) return List.of();
    return ids.stream()
        .filter(Objects::nonNull)
        .filter(id -> id > 0L)
        .distinct()
        .toList();
  }
}
