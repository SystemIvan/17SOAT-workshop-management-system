package br.com.fiap.workshop_management_system.infrastructure.servicelifecycle.serviceorder.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ServiceOrderJpaRepository extends JpaRepository<ServiceOrderJpaEntity, UUID> {
}
