package br.com.fiap.workshop_management_system.registration.customer.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerJpaRepository extends JpaRepository<CustomerJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select customer from CustomerJpaEntity customer where customer.id = :id")
    Optional<CustomerJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    Optional<CustomerJpaEntity> findByDocumentAndActiveTrue(String document);

    boolean existsByDocument(String document);

    List<CustomerJpaEntity> findAllByActiveTrue();
}
