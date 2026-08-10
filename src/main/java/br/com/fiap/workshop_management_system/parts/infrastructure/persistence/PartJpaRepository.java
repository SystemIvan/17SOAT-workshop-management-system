package br.com.fiap.workshop_management_system.parts.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PartJpaRepository extends JpaRepository<PartJpaEntity, UUID> {
}
