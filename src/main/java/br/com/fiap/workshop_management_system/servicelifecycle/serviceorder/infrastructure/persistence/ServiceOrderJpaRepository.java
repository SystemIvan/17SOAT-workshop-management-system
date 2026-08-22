package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ServiceOrderJpaRepository extends JpaRepository<ServiceOrderJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select serviceOrder from ServiceOrderJpaEntity serviceOrder where serviceOrder.id = :id")
    Optional<ServiceOrderJpaEntity> findByIdForUpdate(@Param("id") UUID id);
}
