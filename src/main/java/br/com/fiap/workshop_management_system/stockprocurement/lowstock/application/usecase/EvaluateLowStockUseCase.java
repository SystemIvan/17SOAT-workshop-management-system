package br.com.fiap.workshop_management_system.stockprocurement.lowstock.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.lowstock.application.event.LowStockDetectedEvent;
import br.com.fiap.workshop_management_system.stockprocurement.lowstock.domain.model.LowStockClosureReason;
import br.com.fiap.workshop_management_system.stockprocurement.lowstock.domain.model.LowStockOccurrence;
import br.com.fiap.workshop_management_system.stockprocurement.lowstock.domain.repository.LowStockOccurrenceRepository;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.LowStockPurchaseDemandCommand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.PurchaseDemandApi;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.LowStockAssessment;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.LowStockPolicy;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.LowStockStatus;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItem;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.Optional;

@Service
public class EvaluateLowStockUseCase {

    private final LowStockOccurrenceRepository occurrenceRepository;
    private final PurchaseDemandApi purchaseDemandApi;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Autowired
    public EvaluateLowStockUseCase(LowStockOccurrenceRepository occurrenceRepository, PurchaseDemandApi purchaseDemandApi,
                                   ApplicationEventPublisher eventPublisher) {
        this(occurrenceRepository, purchaseDemandApi, eventPublisher, Clock.systemUTC());
    }

    EvaluateLowStockUseCase(LowStockOccurrenceRepository occurrenceRepository, PurchaseDemandApi purchaseDemandApi,
                            ApplicationEventPublisher eventPublisher, Clock clock) {
        this.occurrenceRepository = occurrenceRepository;
        this.purchaseDemandApi = purchaseDemandApi;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public Optional<LowStockOccurrence> evaluateLockedStockItem(StockItem stockItem) {
        Instant evaluatedAt = currentTime();
        LowStockOccurrence occurrence = occurrenceRepository.findOpenByStockItemIdForUpdate(stockItem.id()).orElse(null);
        LowStockAssessment assessment = stockItem.assessLowStock();

        if (assessment.status() == LowStockStatus.NOT_CONFIGURED) {
            close(occurrence, stockItem.id(), stockItem.active()
                    ? LowStockClosureReason.POLICY_DISABLED : LowStockClosureReason.STOCK_ITEM_DEACTIVATED, evaluatedAt);
            return Optional.empty();
        }
        if (assessment.status() == LowStockStatus.NORMAL) {
            close(occurrence, stockItem.id(), LowStockClosureReason.STOCK_RECOVERED, evaluatedAt);
            return Optional.empty();
        }

        int observedQuantity = stockItem.availableQuantity().value();
        int suggestedQuantity = assessment.suggestedPurchaseQuantity().value();
        if (occurrence == null) {
            UUID occurrenceId = UUID.randomUUID();
            var demand = purchaseDemandApi.recordLowStock(new LowStockPurchaseDemandCommand(
                    occurrenceId, stockItem.id(), observedQuantity, suggestedQuantity));
            LowStockOccurrence opened = LowStockOccurrence.open(
                    occurrenceId, stockItem.id(), demand.purchaseDemandId(), observedQuantity, suggestedQuantity, evaluatedAt);
            occurrenceRepository.save(opened);
            publishDetectedEvent(opened, stockItem.lowStockPolicy());
            return Optional.of(opened);
        }

        if (occurrence.updateObservation(observedQuantity, suggestedQuantity, evaluatedAt)) {
            purchaseDemandApi.recordLowStock(new LowStockPurchaseDemandCommand(
                    occurrence.id(), stockItem.id(), observedQuantity, suggestedQuantity));
            occurrenceRepository.save(occurrence);
        }
        return Optional.of(occurrence);
    }

    public void closeOpenOccurrence(UUID stockItemId, LowStockClosureReason reason) {
        LowStockOccurrence occurrence = occurrenceRepository.findOpenByStockItemIdForUpdate(stockItemId).orElse(null);
        close(occurrence, stockItemId, reason, currentTime());
    }

    private void close(LowStockOccurrence occurrence, UUID stockItemId, LowStockClosureReason reason, Instant closedAt) {
        if (occurrence == null || !occurrence.close(reason, closedAt)) {
            return;
        }
        purchaseDemandApi.resolveLowStock(new br.com.fiap.workshop_management_system.stockprocurement.purchaseorder
                .application.api.LowStockPurchaseDemandResolutionCommand(occurrence.id(), stockItemId, closedAt));
        occurrenceRepository.save(occurrence);
    }

    private void publishDetectedEvent(LowStockOccurrence occurrence, LowStockPolicy policy) {
        eventPublisher.publishEvent(new LowStockDetectedEvent(
                occurrence.id(), occurrence.stockItemId(), occurrence.observedAvailableQuantity(),
                policy.minimumQuantity().value(), policy.targetQuantity().value(), occurrence.suggestedQuantity(),
                occurrence.detectedAt()));
    }

    private Instant currentTime() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
