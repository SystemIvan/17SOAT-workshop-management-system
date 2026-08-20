package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Priority;
import jakarta.validation.constraints.NotNull;

public record ChangeServiceOrderPriorityRequest(
        @NotNull Priority priority) {
}
