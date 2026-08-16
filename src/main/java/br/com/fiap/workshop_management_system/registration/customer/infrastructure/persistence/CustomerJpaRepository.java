package br.com.fiap.workshop_management_system.registration.customer.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerJpaRepository extends JpaRepository<CustomerJpaEntity, UUID> {

    Optional<CustomerJpaEntity> findByDocument(String document);

    boolean existsByDocument(String document);
}
