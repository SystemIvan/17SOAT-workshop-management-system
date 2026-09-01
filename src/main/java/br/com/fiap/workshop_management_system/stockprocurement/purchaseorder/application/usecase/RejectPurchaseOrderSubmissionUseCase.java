package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception.PurchaseDemandNotFoundException;
import br.com.fiap.workshop_management_system.stockprocurement.lowstock.domain.model.LowStockOccurrenceStatus;
import br.com.fiap.workshop_management_system.stockprocurement.lowstock.domain.repository.LowStockOccurrenceRepository;
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
    private final LowStockOccurrenceRepository occurrenceRepository;
    private final Clock clock;

    @Autowired
    public RejectPurchaseOrderSubmissionUseCase(
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseDemandRepository demandRepository,
            LowStockOccurrenceRepository occurrenceRepository) {
        this(purchaseOrderRepository, demandRepository, occurrenceRepository, Clock.systemUTC());
    }

    RejectPurchaseOrderSubmissionUseCase(
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseDemandRepository demandRepository,
            LowStockOccurrenceRepository occurrenceRepository,
            Clock clock) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.demandRepository = demandRepository;
        this.occurrenceRepository = occurrenceRepository;
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
        demands.forEach(demand -> resolveReleasedClosedLowStockDemand(demand, rejectedAt));
        demandRepository.saveAll(demands);
        purchaseOrderRepository.save(order);
        return order;
    }

    private void resolveReleasedClosedLowStockDemand(PurchaseDemand demand, Instant rejectedAt) {
        if (demand.origin() != br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model
                .PurchaseDemandOrigin.LOW_STOCK) {
            return;
        }
        occurrenceRepository.findByPurchaseDemandIdForUpdate(demand.id())
                .filter(occurrence -> occurrence.status() == LowStockOccurrenceStatus.CLOSED)
                .ifPresent(occurrence -> demand.resolve(rejectedAt));
    }

    private Instant currentTime() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
