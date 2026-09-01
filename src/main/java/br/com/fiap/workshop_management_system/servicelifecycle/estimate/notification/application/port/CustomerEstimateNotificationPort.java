package br.com.fiap.workshop_management_system.servicelifecycle.estimate.notification.application.port;

import java.time.Instant;
import java.util.UUID;

/**
 * Consumer-owned outbound port for notifying a Customer that an Estimate was generated.
 * See docs/adr/ADR-004-notifications-boundary.md: Notifications is not a bounded context,
 * so servicelifecycle owns this port and its infrastructure adapter.
 */
public interface CustomerEstimateNotificationPort {

    void notifyEstimateGenerated(UUID estimateId, UUID serviceOrderId, UUID customerId, Instant expiresAt);
}
