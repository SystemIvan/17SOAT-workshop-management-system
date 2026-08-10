package br.com.fiap.workshop_management_system.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.serviceorder.domain.repository.ServiceOrderRepository;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Shared lookup used by every use case in this package to avoid repeating the
 * same not-found handling.
 */
final class ServiceOrderFinder {

    private ServiceOrderFinder() {
    }

    static ServiceOrder getOrThrow(ServiceOrderRepository repository, UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("ServiceOrder not found: " + id));
    }
}
