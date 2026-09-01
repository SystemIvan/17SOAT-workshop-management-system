package br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception.PurchaseOrderNotFoundException;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseOrder;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.repository.PurchaseOrderRepository;
import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.application.dto.StockReceiptResponse;
import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.application.dto.StockReceiptResponseMapper;
import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.application.exception.StockReceiptNotFoundException;
import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.domain.model.StockReceipt;
import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.domain.repository.StockReceiptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetStockReceiptUseCase {

    private final StockReceiptRepository receiptRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    public GetStockReceiptUseCase(
            StockReceiptRepository receiptRepository, PurchaseOrderRepository purchaseOrderRepository) {
        this.receiptRepository = receiptRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
    }

    @Transactional(readOnly = true)
    public StockReceiptResponse execute(UUID purchaseOrderId) {
        StockReceipt receipt = receiptRepository.findByPurchaseOrderId(purchaseOrderId)
                .orElseThrow(StockReceiptNotFoundException::new);
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(PurchaseOrderNotFoundException::new);
        return StockReceiptResponseMapper.toResponse(receipt, purchaseOrder);
    }
}
