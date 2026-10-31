package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.event;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrderStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * RF52 - the derived status of a ServiceOrder changed between the state it was loaded (or created) with and the
 * state being persisted. Carries the internal status only; deciding whether the nominal (RF39) status changed is
 * up to the consumer. Holds opaque IDs only, never customer personal data.
 */
public record ServiceOrderStatusChanged(
        UUID serviceOrderId,
        UUID customerId,
        ServiceOrderStatus previousStatus,
        ServiceOrderStatus currentStatus,
        Instant occurredAt) {

    public ServiceOrderStatusChanged {
        Objects.requireNonNull(serviceOrderId, "serviceOrderId must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(previousStatus, "previousStatus must not be null");
        Objects.requireNonNull(currentStatus, "currentStatus must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
