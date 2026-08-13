package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model;

public enum ServiceExecutionStatus {
    PENDING,
    AUTHORIZED,
    REJECTED,
    AWAITING_PART,
    READY,
    IN_PROGRESS,
    COMPLETED
}