package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderStatusLabel;

import java.util.UUID;

/**
 * Consumer-owned outbound port for notifying a Customer about Service Order events.
 * See docs/adr/ADR-004-notifications-boundary.md: Notifications is not a bounded context,
 * so servicelifecycle owns this port and its infrastructure adapter.
 */
public interface CustomerNotificationPort {

    /**
     * RF52 - the nominal (RF39) status of the Service Order changed. Also covers the former RF33 "finalized"
     * notice, consolidated into this one (docs/features/servicelifecycle/notifications-so-status-change).
     */
    void notifyServiceOrderStatusChanged(UUID serviceOrderId, UUID customerId, ServiceOrderStatusLabel newStatus);
}
