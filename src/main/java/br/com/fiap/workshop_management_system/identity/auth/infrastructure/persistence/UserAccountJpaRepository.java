package br.com.fiap.workshop_management_system.identity.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountJpaRepository extends JpaRepository<UserAccountJpaEntity, UUID> {

    Optional<UserAccountJpaEntity> findByUsername(String username);

    boolean existsByUsername(String username);
}
