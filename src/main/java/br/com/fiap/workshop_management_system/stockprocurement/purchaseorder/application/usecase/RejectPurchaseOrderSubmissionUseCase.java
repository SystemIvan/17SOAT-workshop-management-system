package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception.PurchaseDemandNotFoundException;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception.PurchaseOrderNotFoundException;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseDemand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseOrder;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.repository.PurchaseDemandRepository;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.repository.PurchaseOrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class RejectPurchaseOrderSubmissionUseCase {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseDemandRepository demandRepository;
    private final Clock clock;

    @Autowired
    public RejectPurchaseOrderSubmissionUseCase(
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseDemandRepository demandRepository) {
        this(purchaseOrderRepository, demandRepository, Clock.systemUTC());
    }

    RejectPurchaseOrderSubmissionUseCase(
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseDemandRepository demandRepository,
            Clock clock) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.demandRepository = demandRepository;
        this.clock = clock;
    }

    @Transactional
    public PurchaseOrder execute(UUID purchaseOrderId, String rejectionCode) {
        PurchaseOrder order = purchaseOrderRepository.findByIdForUpdate(purchaseOrderId)
                .orElseThrow(PurchaseOrderNotFoundException::new);
        Instant rejectedAt = currentTime();
        List<UUID> demandIds = order.selectedDemandIds().stream().sorted().toList();
        List<PurchaseDemand> demands = demandRepository.findAllByIdForUpdate(demandIds);
        if (demands.size() != demandIds.size()) {
            throw new PurchaseDemandNotFoundException();
        }

        order.reject(rejectionCode, rejectedAt);
        demands.forEach(demand -> demand.release(order.id(), rejectedAt));
        demandRepository.saveAll(demands);
        purchaseOrderRepository.save(order);
        return order;
    }

    private Instant currentTime() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
