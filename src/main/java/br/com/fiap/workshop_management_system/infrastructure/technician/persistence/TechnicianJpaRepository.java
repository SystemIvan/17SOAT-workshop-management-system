package br.com.fiap.workshop_management_system.infrastructure.technician.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TechnicianJpaRepository extends JpaRepository<TechnicianJpaEntity, UUID> {
}
