package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.dto.PurchaseOrderResponse;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.dto.PurchaseOrderResponseMapper;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception.PurchaseOrderNotFoundException;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseOrderStatus;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.repository.PurchaseOrderRepository;
import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.domain.repository.StockReceiptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetPurchaseOrderUseCase {

    private final PurchaseOrderRepository repository;
    private final StockReceiptRepository receiptRepository;

    public GetPurchaseOrderUseCase(PurchaseOrderRepository repository, StockReceiptRepository receiptRepository) {
        this.repository = repository;
        this.receiptRepository = receiptRepository;
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse execute(UUID purchaseOrderId) {
        return repository.findById(purchaseOrderId)
                .filter(order -> order.status() == PurchaseOrderStatus.OPEN || order.status() == PurchaseOrderStatus.CLOSED)
                .map(order -> PurchaseOrderResponseMapper.toResponse(
                        order, receiptRepository.findByPurchaseOrderId(order.id()).orElse(null)))
                .orElseThrow(PurchaseOrderNotFoundException::new);
    }
}
