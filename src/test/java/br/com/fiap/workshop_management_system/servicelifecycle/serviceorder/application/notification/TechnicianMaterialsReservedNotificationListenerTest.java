package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.notification;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.event.TechnicianMaterialsReservedEvent;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.TechnicianNotificationPort;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TechnicianMaterialsReservedNotificationListenerTest {

    private final TechnicianNotificationPort notificationPort = mock(TechnicianNotificationPort.class);
    private final TechnicianMaterialsReservedNotificationListener listener =
            new TechnicianMaterialsReservedNotificationListener(notificationPort);

    @Test
    void notifiesTheAssignedTechnician() {
        TechnicianMaterialsReservedEvent event = new TechnicianMaterialsReservedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        listener.on(event);

        verify(notificationPort).notifyMaterialsReserved(
                event.serviceOrderId(), event.serviceExecutionId(), event.technicianId(), event.stockReservationId());
    }

    @Test
    void doesNotPropagateDeliveryFailures() {
        TechnicianMaterialsReservedEvent event = new TechnicianMaterialsReservedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        doThrow(new RuntimeException("delivery failed"))
                .when(notificationPort)
                .notifyMaterialsReserved(
                        event.serviceOrderId(),
                        event.serviceExecutionId(),
                        event.technicianId(),
                        event.stockReservationId());

        assertDoesNotThrow(() -> listener.on(event));
    }
}
