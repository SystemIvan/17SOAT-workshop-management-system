package br.com.fiap.workshop_management_system.serviceorder.application.dto;

import br.com.fiap.workshop_management_system.serviceorder.domain.model.ServiceOrderStatus;

import java.util.UUID;

public record ServiceOrderStatusResponse(
        UUID id,
        ServiceOrderStatus status) {
}
