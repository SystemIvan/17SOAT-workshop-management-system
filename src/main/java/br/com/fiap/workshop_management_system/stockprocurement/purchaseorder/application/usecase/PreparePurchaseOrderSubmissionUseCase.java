package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.command.CreatePurchaseOrderCommand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception.InvalidPurchaseOrderException;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception.PurchaseDemandNotFoundException;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception.PurchaseOrderIdempotencyConflictException;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseDemand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseDemandNotSelectableException;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseDemandStatus;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseOrder;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseOrderLine;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.repository.PurchaseDemandRepository;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.repository.PurchaseOrderRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PreparePurchaseOrderSubmissionUseCase {

    private final PurchaseOrderCommandNormalizer normalizer;
    private final PurchaseDemandRepository demandRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final StockItemRepository stockItemRepository;
    private final Clock clock;

    @Autowired
    public PreparePurchaseOrderSubmissionUseCase(
            PurchaseOrderCommandNormalizer normalizer,
            PurchaseDemandRepository demandRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            StockItemRepository stockItemRepository) {
        this(normalizer, demandRepository, purchaseOrderRepository, stockItemRepository, Clock.systemUTC());
    }

    PreparePurchaseOrderSubmissionUseCase(
            PurchaseOrderCommandNormalizer normalizer,
            PurchaseDemandRepository demandRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            StockItemRepository stockItemRepository,
            Clock clock) {
        this.normalizer = normalizer;
        this.demandRepository = demandRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.stockItemRepository = stockItemRepository;
        this.clock = clock;
    }

    @Transactional
    public PreparedPurchaseOrder execute(UUID idempotencyKey, CreatePurchaseOrderCommand command) {
        if (idempotencyKey == null) {
            throw new InvalidPurchaseOrderException("Idempotency key must not be null");
        }
        NormalizedPurchaseOrderCommand normalized = normalizer.normalize(command);
        var existing = purchaseOrderRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            assertSamePayload(existing.get(), normalized.payloadHash());
            return new PreparedPurchaseOrder(existing.get(), false);
        }

        List<PurchaseDemand> demands = lockDemands(normalized.demandIds());
        Map<UUID, StockItem> stockItems = lockStockItems(normalized);
        validateDemandCoverage(demands, normalized, stockItems);

        List<PurchaseOrderLine> lines = normalized.lines().stream()
                .map(line -> {
                    StockItem item = stockItems.get(line.stockItemId());
                    return new PurchaseOrderLine(
                            item.id(), item.sku().value(), item.name(), item.type(), line.quantity());
                })
                .toList();
        Instant preparedAt = currentTime();
        PurchaseOrder order = PurchaseOrder.prepare(
                idempotencyKey,
                normalized.payloadHash(),
                lines,
                Set.copyOf(normalized.demandIds()),
                preparedAt);
        demands.forEach(demand -> demand.claim(order.id(), preparedAt));

        purchaseOrderRepository.save(order);
        demandRepository.saveAll(demands);
        purchaseOrderRepository.saveAndFlush(order);
        return new PreparedPurchaseOrder(order, true);
    }

    private List<PurchaseDemand> lockDemands(List<UUID> demandIds) {
        List<PurchaseDemand> demands = demandRepository.findAllByIdForUpdate(demandIds);
        if (demands.size() != demandIds.size()) {
            throw new PurchaseDemandNotFoundException();
        }
        if (demands.stream().anyMatch(demand -> demand.status() != PurchaseDemandStatus.OPEN)) {
            throw new PurchaseDemandNotSelectableException();
        }
        return demands;
    }

    private Map<UUID, StockItem> lockStockItems(NormalizedPurchaseOrderCommand command) {
        List<UUID> ids = command.lines().stream().map(line -> line.stockItemId()).toList();
        Map<UUID, StockItem> items = new HashMap<>();
        stockItemRepository.findAllByIdForUpdate(ids).forEach(item -> items.put(item.id(), item));
        if (items.size() != ids.size()) {
            throw new StockItemNotFoundException();
        }
        if (items.values().stream().anyMatch(item -> !item.active())) {
            throw new StockItemInactiveException();
        }
        return items;
    }

    private void validateDemandCoverage(
            List<PurchaseDemand> demands,
            NormalizedPurchaseOrderCommand command,
            Map<UUID, StockItem> stockItems) {
        Map<UUID, Integer> orderedByItem = new HashMap<>();
        command.lines().forEach(line -> orderedByItem.put(line.stockItemId(), line.quantity()));
        Map<UUID, Integer> suggestedByItem = new HashMap<>();
        for (PurchaseDemand demand : demands) {
            if (!stockItems.containsKey(demand.stockItemId())) {
                throw new InvalidPurchaseOrderException("Every selected demand requires a matching purchase line");
            }
            try {
                suggestedByItem.merge(demand.stockItemId(), demand.suggestedQuantity(), Math::addExact);
            } catch (ArithmeticException exception) {
                throw new InvalidPurchaseOrderException(
                        "Selected demand quantity exceeds the supported range", exception);
            }
        }
        suggestedByItem.forEach((stockItemId, suggestedQuantity) -> {
            if (orderedByItem.get(stockItemId) < suggestedQuantity) {
                throw new InvalidPurchaseOrderException(
                        "Purchase order quantity is lower than the selected demand suggestion");
            }
        });
    }

    private void assertSamePayload(PurchaseOrder order, String payloadHash) {
        if (!order.payloadHash().equals(payloadHash)) {
            throw new PurchaseOrderIdempotencyConflictException();
        }
    }

    private Instant currentTime() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
