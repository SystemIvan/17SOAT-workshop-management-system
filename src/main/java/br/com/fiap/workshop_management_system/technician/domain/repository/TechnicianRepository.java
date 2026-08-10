package br.com.fiap.workshop_management_system.technician.domain.repository;

import br.com.fiap.workshop_management_system.technician.domain.model.Technician;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port for the Technician aggregate. Each Aggregate Root has its own repository;
 * transactional consistency is restricted to the aggregate's own boundary.
 */
public interface TechnicianRepository {

    Optional<Technician> findById(UUID id);

    List<Technician> findAll();

    void save(Technician technician);
}
