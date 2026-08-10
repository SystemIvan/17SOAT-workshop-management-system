package br.com.fiap.workshop_management_system.serviceorder.domain.model;

public enum ServiceOrderStatus {
    RECEIVED,
    IN_DIAGNOSIS,
    AWAITING_APPROVAL,
    AWAITING_PART,
    IN_PROGRESS,
    COMPLETED,
    DELIVERED
}