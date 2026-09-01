package br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception.PurchaseOrderNotFoundException;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseOrder;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseOrderLine;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseOrderStatus;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.repository.PurchaseOrderRepository;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Quantity;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItemReceiptBalance;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;
import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.application.dto.ReceivePurchaseOrderResult;
import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.application.event.StockItemsRestockedEvent;
import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.application.exception.PurchaseOrderNotClosedException;
import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.application.exception.StockQuantityOverflowException;
import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.application.exception.StockReceiptInconsistentException;
import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.domain.model.StockReceipt;
import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.domain.model.StockReceiptLine;
import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.domain.repository.StockReceiptRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReceivePurchaseOrderUseCase {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final StockReceiptRepository receiptRepository;
    private final StockItemRepository stockItemRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Autowired
    public ReceivePurchaseOrderUseCase(
            PurchaseOrderRepository purchaseOrderRepository,
            StockReceiptRepository receiptRepository,
            StockItemRepository stockItemRepository,
            ApplicationEventPublisher eventPublisher) {
        this(purchaseOrderRepository, receiptRepository, stockItemRepository, eventPublisher, Clock.systemUTC());
    }

    ReceivePurchaseOrderUseCase(
            PurchaseOrderRepository purchaseOrderRepository,
            StockReceiptRepository receiptRepository,
            StockItemRepository stockItemRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.receiptRepository = receiptRepository;
        this.stockItemRepository = stockItemRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public ReceivePurchaseOrderResult execute(UUID purchaseOrderId, UUID userAccountId) {
        if (purchaseOrderId == null || userAccountId == null) {
            throw new IllegalArgumentException("Purchase order and user account identifiers must not be null");
        }
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findByIdForUpdate(purchaseOrderId)
                .orElseThrow(PurchaseOrderNotFoundException::new);
        requireClosed(purchaseOrder);

        StockReceipt existing = receiptRepository.findByPurchaseOrderIdForUpdate(purchaseOrderId).orElse(null);
        if (existing != null) {
            publishRestockedEvent(existing);
            return new ReceivePurchaseOrderResult(existing, false);
        }

        Map<UUID, StockItem> stockItems = lockStockItems(purchaseOrder);
        Instant receivedAt = currentTime();
        List<StockReceiptLine> lines = receiveLines(purchaseOrder, stockItems);
        StockReceipt receipt = StockReceipt.create(purchaseOrderId, userAccountId, receivedAt, lines);
        receiptRepository.save(receipt);
        stockItems.values().forEach(stockItemRepository::save);
        publishRestockedEvent(receipt);
        return new ReceivePurchaseOrderResult(receipt, true);
    }

    private void requireClosed(PurchaseOrder purchaseOrder) {
        if (purchaseOrder.status() == PurchaseOrderStatus.OPEN) {
            throw new PurchaseOrderNotClosedException();
        }
        if (purchaseOrder.status() != PurchaseOrderStatus.CLOSED) {
            throw new PurchaseOrderNotFoundException();
        }
    }

    private Map<UUID, StockItem> lockStockItems(PurchaseOrder purchaseOrder) {
        List<UUID> stockItemIds = purchaseOrder.lines().stream().map(PurchaseOrderLine::stockItemId).sorted().toList();
        Map<UUID, StockItem> stockItems = new HashMap<>();
        stockItemRepository.findAllByIdForUpdate(stockItemIds).forEach(item -> stockItems.put(item.id(), item));
        if (stockItems.size() != stockItemIds.size()) {
            throw new StockReceiptInconsistentException();
        }
        return stockItems;
    }

    private List<StockReceiptLine> receiveLines(PurchaseOrder purchaseOrder, Map<UUID, StockItem> stockItems) {
        try {
            return purchaseOrder.lines().stream().map(line -> {
                StockItemReceiptBalance balance = stockItems.get(line.stockItemId()).receive(new Quantity(line.quantity()));
                return new StockReceiptLine(
                        UUID.randomUUID(),
                        line.stockItemId(),
                        line.quantity(),
                        balance.availableBefore().value(),
                        balance.availableAfter().value());
            }).toList();
        } catch (ArithmeticException exception) {
            throw new StockQuantityOverflowException(exception);
        }
    }

    private void publishRestockedEvent(StockReceipt receipt) {
        eventPublisher.publishEvent(new StockItemsRestockedEvent(
                receipt.id(),
                receipt.purchaseOrderId(),
                receipt.lines().stream().map(StockReceiptLine::stockItemId).toList(),
                receipt.receivedAt()));
    }

    private Instant currentTime() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
