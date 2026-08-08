package br.com.fiap.workshop_management_system.domain.parts.repository;

import br.com.fiap.workshop_management_system.domain.parts.model.Part;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port for the Part aggregate. Each Aggregate Root has its own repository;
 * transactional consistency is restricted to the aggregate's own boundary.
 */
public interface PartRepository {

    Optional<Part> findById(UUID id);

    List<Part> findAll();

    void save(Part part);
}
