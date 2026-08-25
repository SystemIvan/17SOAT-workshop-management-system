package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.infrastructure.web;

import br.com.fiap.workshop_management_system.ErrorResponse;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception.ExternalSupplierInvalidResponseException;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception.ExternalSupplierUnavailableException;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception.InvalidPurchaseOrderException;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception.PurchaseDemandNotFoundException;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception.PurchaseOrderIdempotencyConflictException;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception.PurchaseOrderNotFoundException;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception.SupplierOrderRejectedException;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseDemandNotSelectableException;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.exception.StockItemNotFoundException;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItemInactiveException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {PurchaseOrderController.class, PurchaseDemandController.class})
class PurchaseOrderExceptionHandler {

    @ExceptionHandler(InvalidPurchaseOrderException.class)
    ResponseEntity<ErrorResponse> handleInvalidOrder(InvalidPurchaseOrderException exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_PURCHASE_ORDER", "Invalid purchase order"));
    }

    @ExceptionHandler(PurchaseDemandNotFoundException.class)
    ResponseEntity<ErrorResponse> handleDemandNotFound(PurchaseDemandNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("PURCHASE_DEMAND_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(PurchaseOrderNotFoundException.class)
    ResponseEntity<ErrorResponse> handleOrderNotFound(PurchaseOrderNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("PURCHASE_ORDER_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(StockItemNotFoundException.class)
    ResponseEntity<ErrorResponse> handleStockItemNotFound(StockItemNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("STOCK_ITEM_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(PurchaseDemandNotSelectableException.class)
    ResponseEntity<ErrorResponse> handleDemandNotSelectable(PurchaseDemandNotSelectableException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("PURCHASE_DEMAND_NOT_SELECTABLE", exception.getMessage()));
    }

    @ExceptionHandler(StockItemInactiveException.class)
    ResponseEntity<ErrorResponse> handleStockItemInactive(StockItemInactiveException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("STOCK_ITEM_INACTIVE", exception.getMessage()));
    }

    @ExceptionHandler(PurchaseOrderIdempotencyConflictException.class)
    ResponseEntity<ErrorResponse> handleIdempotencyConflict(PurchaseOrderIdempotencyConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("PURCHASE_ORDER_IDEMPOTENCY_CONFLICT", exception.getMessage()));
    }

    @ExceptionHandler(SupplierOrderRejectedException.class)
    ResponseEntity<ErrorResponse> handleSupplierRejected(SupplierOrderRejectedException exception) {
        return ResponseEntity.unprocessableContent()
                .body(new ErrorResponse("SUPPLIER_ORDER_REJECTED", "External supplier rejected the purchase order"));
    }

    @ExceptionHandler(ExternalSupplierUnavailableException.class)
    ResponseEntity<ErrorResponse> handleSupplierUnavailable(ExternalSupplierUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(
                        "EXTERNAL_SUPPLIER_UNAVAILABLE",
                        "External supplier is unavailable; retry with the same request and Idempotency-Key"));
    }

    @ExceptionHandler(ExternalSupplierInvalidResponseException.class)
    ResponseEntity<ErrorResponse> handleSupplierInvalidResponse(ExternalSupplierInvalidResponseException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse(
                        "EXTERNAL_SUPPLIER_INVALID_RESPONSE",
                        "External supplier returned an invalid response"));
    }
}
