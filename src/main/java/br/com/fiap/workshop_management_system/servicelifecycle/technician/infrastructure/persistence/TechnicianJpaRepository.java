package br.com.fiap.workshop_management_system.servicelifecycle.technician.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TechnicianJpaRepository extends JpaRepository<TechnicianJpaEntity, UUID> {
}
