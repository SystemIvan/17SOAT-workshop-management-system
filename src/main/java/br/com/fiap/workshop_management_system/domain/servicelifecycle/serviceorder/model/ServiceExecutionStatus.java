package br.com.fiap.workshop_management_system.domain.servicelifecycle.serviceorder.model;

public enum ServiceExecutionStatus {
    PENDING,
    AUTHORIZED,
    REJECTED,
    AWAITING_PART,
    READY,
    IN_PROGRESS,
    COMPLETED
}