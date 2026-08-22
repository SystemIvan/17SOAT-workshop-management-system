package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.notification;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.TechnicianNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Channel decision recorded in technical-spec.md: Technician has no contact information, so
 * recipients are addressed only by technicianId - a structured log line is enough for this MVP.
 */
@Component
public class LoggedTechnicianNotificationAdapter implements TechnicianNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(LoggedTechnicianNotificationAdapter.class);

    @Override
    public void notifyServiceOrderCreated(UUID serviceOrderId, UUID technicianId) {
        log.info("New service order available | technicianId={} | serviceOrderId={}", technicianId, serviceOrderId);
    }

    @Override
    public void notifyMaterialsReserved(
            UUID serviceOrderId,
            UUID serviceExecutionId,
            UUID technicianId,
            UUID stockReservationId) {
        log.info("Materials reserved for pickup | technicianId={} | serviceOrderId={} | serviceExecutionId={} "
                        + "| stockReservationId={}",
                technicianId, serviceOrderId, serviceExecutionId, stockReservationId);
    }
}
