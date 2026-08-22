package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model;

public enum ServiceExecutionStatus {
    PENDING,
    AUTHORIZED,
    REJECTED,
    AWAITING_ITEMS,
    READY,
    IN_PROGRESS,
    COMPLETED
}
