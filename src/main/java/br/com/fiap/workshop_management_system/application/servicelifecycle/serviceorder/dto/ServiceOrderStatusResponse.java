package br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.dto;

import br.com.fiap.workshop_management_system.domain.servicelifecycle.serviceorder.model.ServiceOrderStatus;

import java.util.UUID;

public record ServiceOrderStatusResponse(
        UUID id,
        ServiceOrderStatus status) {
}
