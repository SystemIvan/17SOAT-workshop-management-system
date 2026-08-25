package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.command.CreatePurchaseOrderCommand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.dto.CreatePurchaseOrderResult;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.dto.PurchaseOrderResponseMapper;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception.PurchaseOrderIdempotencyRaceException;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception.SupplierOrderRejectedException;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.port.ExternalPurchaseOrderCommand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.port.ExternalPurchaseOrderLine;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.port.ExternalPurchaseOrderResult;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.port.ExternalSupplierGateway;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseOrder;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseOrderStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CreatePurchaseOrderUseCase {

    private final PreparePurchaseOrderSubmissionUseCase prepareSubmission;
    private final ConfirmPurchaseOrderSubmissionUseCase confirmSubmission;
    private final RejectPurchaseOrderSubmissionUseCase rejectSubmission;
    private final ExternalSupplierGateway externalSupplierGateway;

    public CreatePurchaseOrderUseCase(
            PreparePurchaseOrderSubmissionUseCase prepareSubmission,
            ConfirmPurchaseOrderSubmissionUseCase confirmSubmission,
            RejectPurchaseOrderSubmissionUseCase rejectSubmission,
            ExternalSupplierGateway externalSupplierGateway) {
        this.prepareSubmission = prepareSubmission;
        this.confirmSubmission = confirmSubmission;
        this.rejectSubmission = rejectSubmission;
        this.externalSupplierGateway = externalSupplierGateway;
    }

    public CreatePurchaseOrderResult execute(UUID idempotencyKey, CreatePurchaseOrderCommand command) {
        PreparedPurchaseOrder prepared;
        try {
            prepared = prepareSubmission.execute(idempotencyKey, command);
        } catch (PurchaseOrderIdempotencyRaceException exception) {
            prepared = prepareSubmission.execute(idempotencyKey, command);
        }
        PurchaseOrder order = prepared.purchaseOrder();
        if (order.status() == PurchaseOrderStatus.OPEN) {
            return new CreatePurchaseOrderResult(PurchaseOrderResponseMapper.toResponse(order), false);
        }
        if (order.status() == PurchaseOrderStatus.REJECTED) {
            throw new SupplierOrderRejectedException(order.supplierRejectionCode());
        }

        ExternalPurchaseOrderResult result = externalSupplierGateway.submit(toExternalCommand(order));
        if (result instanceof ExternalPurchaseOrderResult.Accepted accepted) {
            PurchaseOrder confirmed = confirmSubmission.execute(order.id(), accepted.externalReference());
            return new CreatePurchaseOrderResult(PurchaseOrderResponseMapper.toResponse(confirmed), true);
        }
        ExternalPurchaseOrderResult.Rejected rejected = (ExternalPurchaseOrderResult.Rejected) result;
        rejectSubmission.execute(order.id(), rejected.rejectionCode());
        throw new SupplierOrderRejectedException(rejected.rejectionCode());
    }

    private ExternalPurchaseOrderCommand toExternalCommand(PurchaseOrder order) {
        return new ExternalPurchaseOrderCommand(
                order.id(),
                order.idempotencyKey(),
                order.lines().stream()
                        .map(line -> new ExternalPurchaseOrderLine(line.skuSnapshot(), line.quantity()))
                        .toList());
    }
}
