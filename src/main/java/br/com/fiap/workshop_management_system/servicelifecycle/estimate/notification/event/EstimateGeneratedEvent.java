package br.com.fiap.workshop_management_system.servicelifecycle.estimate.notification.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Local mock of the Estimate-generated event contract confirmed with the owner of
 * servicelifecycle.estimate (see docs/features/servicelifecycle/notifications-estimate-generated/functional-spec.md,
 * Decision record A). Not imported from feat/servicelifecycle-estimate-generation - reconciliation with the real
 * event published by that module is a separate follow-up step.
 */
public record EstimateGeneratedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID estimateId,
        UUID serviceOrderId,
        UUID diagnosisId,
        UUID customerId,
        Instant expiresAt) {
}
