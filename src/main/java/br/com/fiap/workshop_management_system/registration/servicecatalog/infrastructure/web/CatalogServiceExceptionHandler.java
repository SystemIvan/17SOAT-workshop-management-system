package br.com.fiap.workshop_management_system.registration.servicecatalog.infrastructure.web;

import br.com.fiap.workshop_management_system.ErrorResponse;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.exception
        .CatalogServiceNameAlreadyExistsException;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.exception
        .CatalogServiceNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = CatalogServiceController.class)
class CatalogServiceExceptionHandler {

    @ExceptionHandler(CatalogServiceNotFoundException.class)
    ResponseEntity<ErrorResponse> handleNotFound(CatalogServiceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("CATALOG_SERVICE_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(CatalogServiceNameAlreadyExistsException.class)
    ResponseEntity<ErrorResponse> handleDuplicateName(CatalogServiceNameAlreadyExistsException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CATALOG_SERVICE_NAME_ALREADY_EXISTS", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> handleInvalidContract() {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_ERROR", "Requisição inválida"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> handleInvalidCatalogService(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_CATALOG_SERVICE", exception.getMessage()));
    }
}
