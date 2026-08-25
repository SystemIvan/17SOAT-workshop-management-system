package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.LowStockPurchaseDemandCommand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.PurchaseDemandApi;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseDemand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseDemandOrigin;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.repository.PurchaseDemandRepository;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.exception.StockItemNotFoundException;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItemInactiveException;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class RecordLowStockPurchaseDemandUseCase implements PurchaseDemandApi {

    private final PurchaseDemandRepository demandRepository;
    private final StockItemRepository stockItemRepository;
    private final Clock clock;

    @Autowired
    public RecordLowStockPurchaseDemandUseCase(
            PurchaseDemandRepository demandRepository,
            StockItemRepository stockItemRepository) {
        this(demandRepository, stockItemRepository, Clock.systemUTC());
    }

    RecordLowStockPurchaseDemandUseCase(
            PurchaseDemandRepository demandRepository,
            StockItemRepository stockItemRepository,
            Clock clock) {
        this.demandRepository = demandRepository;
        this.stockItemRepository = stockItemRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void recordLowStock(LowStockPurchaseDemandCommand command) {
        StockItem stockItem = stockItemRepository.findByIdForUpdate(command.stockItemId())
                .orElseThrow(StockItemNotFoundException::new);
        if (!stockItem.active()) {
            throw new StockItemInactiveException();
        }

        Instant observedAt = currentTime();
        PurchaseDemand demand = demandRepository.findEquivalentForUpdate(
                        PurchaseDemandOrigin.LOW_STOCK,
                        command.occurrenceId(),
                        command.stockItemId())
                .orElseGet(() -> PurchaseDemand.createLowStock(
                        command.occurrenceId(),
                        command.stockItemId(),
                        command.observedAvailableQuantity(),
                        command.suggestedQuantity(),
                        observedAt));
        demand.recordObservation(
                null,
                command.observedAvailableQuantity(),
                command.suggestedQuantity(),
                observedAt);
        demandRepository.save(demand);
    }

    private Instant currentTime() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
