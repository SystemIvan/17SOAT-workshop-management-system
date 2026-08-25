package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseDemand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseDemandOrigin;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.repository.PurchaseDemandRepository;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.StockReservationIssue;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.StockReservationIssueReason;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.event.StockReservationCreatedEvent;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.event.StockReservationNotReservedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;

@Component
public class RecordPendingRepairDemandUseCase {

    private final PurchaseDemandRepository repository;
    private final Clock clock;

    @Autowired
    public RecordPendingRepairDemandUseCase(PurchaseDemandRepository repository) {
        this(repository, Clock.systemUTC());
    }

    RecordPendingRepairDemandUseCase(PurchaseDemandRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @EventListener
    public void onReservationNotReserved(StockReservationNotReservedEvent event) {
        event.issues().stream()
                .filter(issue -> issue.reason() == StockReservationIssueReason.INSUFFICIENT_QUANTITY)
                .sorted(Comparator.comparing(StockReservationIssue::stockItemId))
                .forEach(issue -> recordInsufficientStock(event, issue));
    }

    @EventListener
    public void onReservationCreated(StockReservationCreatedEvent event) {
        event.items().stream()
                .sorted(Comparator.comparing(item -> item.stockItemId()))
                .forEach(item -> repository.findEquivalentForUpdate(
                                PurchaseDemandOrigin.PENDING_REPAIR,
                                event.serviceExecutionId(),
                                item.stockItemId())
                        .ifPresent(demand -> {
                            demand.resolve(currentTime());
                            repository.save(demand);
                        }));
    }

    private void recordInsufficientStock(
            StockReservationNotReservedEvent event,
            StockReservationIssue issue) {
        if (issue.availableQuantity() == null) {
            throw new IllegalArgumentException("Insufficient stock issue requires the observed available quantity");
        }
        int suggestedQuantity;
        try {
            suggestedQuantity = Math.subtractExact(issue.requestedQuantity(), issue.availableQuantity());
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Purchase demand quantity exceeds the supported range", exception);
        }
        Instant observedAt = currentTime();
        PurchaseDemand demand = repository.findEquivalentForUpdate(
                        PurchaseDemandOrigin.PENDING_REPAIR,
                        event.serviceExecutionId(),
                        issue.stockItemId())
                .orElseGet(() -> PurchaseDemand.createPendingRepair(
                        event.serviceExecutionId(),
                        issue.stockItemId(),
                        issue.requestedQuantity(),
                        issue.availableQuantity(),
                        suggestedQuantity,
                        observedAt));
        demand.recordObservation(
                issue.requestedQuantity(),
                issue.availableQuantity(),
                suggestedQuantity,
                observedAt);
        repository.save(demand);
    }

    private Instant currentTime() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
