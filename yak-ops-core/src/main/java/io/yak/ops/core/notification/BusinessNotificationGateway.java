package io.yak.ops.core.notification;

/** Outbound port for delivering Project-owned business notifications to Project owners. */
public interface BusinessNotificationGateway {

  /**
   * Deliver one business event to the current Project owners.
   *
   * <p>Implementations must treat notification delivery as a secondary side effect: delivery
   * failures must not fail the originating business execution.</p>
   */
  void publishToProjectOwners(BusinessNotification notification);
}
