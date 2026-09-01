package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port;

import java.util.UUID;

/**
 * Consumer-owned outbound port for notifying a Technician about Service Order events.
 * See docs/adr/ADR-004-notifications-boundary.md: Notifications is not a bounded context,
 * so servicelifecycle owns this port and its infrastructure adapter.
 */
public interface TechnicianNotificationPort {

    void notifyServiceOrderCreated(UUID serviceOrderId, UUID technicianId);

    void notifyMaterialsReserved(
            UUID serviceOrderId,
            UUID serviceExecutionId,
            UUID technicianId,
            UUID stockReservationId);
}
