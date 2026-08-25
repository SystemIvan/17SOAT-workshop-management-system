package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.dto.PurchaseOrderResponse;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.dto.PurchaseOrderResponseMapper;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception.PurchaseOrderNotFoundException;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseOrderStatus;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.repository.PurchaseOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetPurchaseOrderUseCase {

    private final PurchaseOrderRepository repository;

    public GetPurchaseOrderUseCase(PurchaseOrderRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse execute(UUID purchaseOrderId) {
        return repository.findById(purchaseOrderId)
                .filter(order -> order.status() == PurchaseOrderStatus.OPEN)
                .map(PurchaseOrderResponseMapper::toResponse)
                .orElseThrow(PurchaseOrderNotFoundException::new);
    }
}
