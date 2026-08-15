package br.com.fiap.workshop_management_system.stockprocurement.stock.infrastructure.web;

import br.com.fiap.workshop_management_system.ErrorResponse;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.exception.StockItemNotFoundException;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.exception
        .StockItemSkuAlreadyExistsException;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItemInactiveException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = StockItemController.class)
class StockItemExceptionHandler {
    @ExceptionHandler(StockItemNotFoundException.class)
    ResponseEntity<ErrorResponse> handleNotFound(StockItemNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("STOCK_ITEM_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(StockItemSkuAlreadyExistsException.class)
    ResponseEntity<ErrorResponse> handleDuplicateSku(StockItemSkuAlreadyExistsException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("STOCK_ITEM_SKU_ALREADY_EXISTS",
                "A stock item with this SKU already exists"));
    }

    @ExceptionHandler(StockItemInactiveException.class)
    ResponseEntity<ErrorResponse> handleInactive(StockItemInactiveException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("STOCK_ITEM_INACTIVE", exception.getMessage()));
    }
}
