package br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Part;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.PartRepository;

import java.util.NoSuchElementException;
import java.util.UUID;

final class PartFinder {

    private PartFinder() {
    }

    static Part getOrThrow(PartRepository repository, UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Part not found: " + id));
    }
}
