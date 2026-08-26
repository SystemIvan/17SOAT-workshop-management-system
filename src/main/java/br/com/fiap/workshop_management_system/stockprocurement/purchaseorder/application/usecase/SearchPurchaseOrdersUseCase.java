package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.dto.PurchaseOrderResponse;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.dto
        .PurchaseOrderResponseMapper;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.dto.PurchaseOrderReceiptStatus;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.dto
        .PurchaseOrderStatusResponse;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseOrder;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseOrderStatus;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.repository.PurchaseOrderRepository;
import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.domain.model.StockReceipt;
import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.domain.repository.StockReceiptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SearchPurchaseOrdersUseCase {

    private final PurchaseOrderRepository repository;
    private final StockReceiptRepository receiptRepository;

    public SearchPurchaseOrdersUseCase(PurchaseOrderRepository repository, StockReceiptRepository receiptRepository) {
        this.repository = repository;
        this.receiptRepository = receiptRepository;
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderResponse> execute(
            Set<PurchaseOrderStatusResponse> statuses, PurchaseOrderReceiptStatus receiptStatus) {
        Set<PurchaseOrderStatusResponse> requestedStatuses = statuses == null || statuses.isEmpty()
                ? EnumSet.allOf(PurchaseOrderStatusResponse.class)
                : EnumSet.copyOf(statuses);
        Set<PurchaseOrderStatus> domainStatuses = requestedStatuses.stream()
                .map(status -> PurchaseOrderStatus.valueOf(status.name()))
                .collect(() -> EnumSet.noneOf(PurchaseOrderStatus.class), Set::add, Set::addAll);
        List<PurchaseOrder> orders = repository.searchConfirmedByStatus(domainStatuses);
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<UUID, StockReceipt> receiptsByOrderId = receiptRepository.findByPurchaseOrderIds(
                        orders.stream().map(order -> order.id()).toList()).stream()
                .collect(Collectors.toMap(StockReceipt::purchaseOrderId, Function.identity()));
        return orders.stream()
                .filter(order -> matchesReceiptStatus(order, receiptsByOrderId, receiptStatus))
                .map(order -> PurchaseOrderResponseMapper.toResponse(order, receiptsByOrderId.get(order.id())))
                .toList();
    }

    private boolean matchesReceiptStatus(
            PurchaseOrder order,
            Map<UUID, StockReceipt> receiptsByOrderId,
            PurchaseOrderReceiptStatus receiptStatus) {
        if (receiptStatus == null) {
            return true;
        }
        boolean received = receiptsByOrderId.containsKey(order.id());
        return switch (receiptStatus) {
            case PENDING -> order.status() == PurchaseOrderStatus.CLOSED && !received;
            case RECEIVED -> order.status() == PurchaseOrderStatus.CLOSED && received;
        };
    }
}
