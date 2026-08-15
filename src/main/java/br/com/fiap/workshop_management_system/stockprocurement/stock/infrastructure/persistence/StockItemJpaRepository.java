package br.com.fiap.workshop_management_system.stockprocurement.stock.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface StockItemJpaRepository extends JpaRepository<StockItemJpaEntity, UUID>,
        JpaSpecificationExecutor<StockItemJpaEntity> {
    boolean existsBySku(String sku);
}
