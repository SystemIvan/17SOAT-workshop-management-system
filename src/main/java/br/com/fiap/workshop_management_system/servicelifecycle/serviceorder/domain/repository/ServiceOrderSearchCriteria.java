package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Priority;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrderStatus;

import java.util.UUID;

/**
 * Optional filters for {@link ServiceOrderRepository#search}. Every field is nullable; a null field
 * means "no filter" and non-null fields are combined with AND.
 */
public record ServiceOrderSearchCriteria(
        ServiceOrderStatus status, UUID customerId, UUID technicianId, Priority priority) {
}
