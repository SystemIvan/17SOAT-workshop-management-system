package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port;

import java.util.UUID;

/**
 * Consumer-owned outbound port for notifying a Customer about Service Order events.
 * See docs/adr/ADR-003-notifications-boundary.md: Notifications is not a bounded context,
 * so servicelifecycle owns this port and its infrastructure adapter.
 */
public interface CustomerNotificationPort {

    void notifyServiceOrderFinalized(UUID serviceOrderId, UUID customerId);
}
