package br.com.fiap.workshop_management_system.application.technician.usecase;

import br.com.fiap.workshop_management_system.domain.technician.model.Technician;
import br.com.fiap.workshop_management_system.domain.technician.repository.TechnicianRepository;

import java.util.NoSuchElementException;
import java.util.UUID;

final class TechnicianFinder {

    private TechnicianFinder() {
    }

    static Technician getOrThrow(TechnicianRepository repository, UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Technician not found: " + id));
    }
}
