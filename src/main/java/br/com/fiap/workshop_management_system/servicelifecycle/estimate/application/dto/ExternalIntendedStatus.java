package br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto;

/**
 * RF40 - the customer's intent extracted from an external channel (e-mail) about the pending estimate of a
 * service order. Kept separate from {@link EstimateLineDecision} so the external contract does not follow
 * changes to the internal per-line decision DTO; the use case maps between them explicitly.
 */
public enum ExternalIntendedStatus {
    APPROVED,
    REJECTED
}
