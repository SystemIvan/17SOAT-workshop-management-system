package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.dto.PurchaseOrderResponse;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.dto.PurchaseOrderResponseMapper;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception.PurchaseOrderNotFoundException;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseOrder;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseOrderStatus;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.repository.PurchaseOrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class ClosePurchaseOrderUseCase {

    private final PurchaseOrderRepository repository;
    private final Clock clock;

    @Autowired
    public ClosePurchaseOrderUseCase(PurchaseOrderRepository repository) {
        this(repository, Clock.systemUTC());
    }

    ClosePurchaseOrderUseCase(PurchaseOrderRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public PurchaseOrderResponse execute(UUID purchaseOrderId, UUID userAccountId) {
        if (purchaseOrderId == null || userAccountId == null) {
            throw new IllegalArgumentException("Purchase order and user account identifiers must not be null");
        }
        PurchaseOrder order = repository.findByIdForUpdate(purchaseOrderId)
                .filter(this::isConfirmed)
                .orElseThrow(PurchaseOrderNotFoundException::new);
        order.close(userAccountId, currentTime());
        repository.save(order);
        return PurchaseOrderResponseMapper.toResponse(order);
    }

    private boolean isConfirmed(PurchaseOrder order) {
        return order.status() == PurchaseOrderStatus.OPEN || order.status() == PurchaseOrderStatus.CLOSED;
    }

    private Instant currentTime() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
