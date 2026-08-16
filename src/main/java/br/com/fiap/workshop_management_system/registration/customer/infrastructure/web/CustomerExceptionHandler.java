package br.com.fiap.workshop_management_system.registration.customer.infrastructure.web;

import br.com.fiap.workshop_management_system.ErrorResponse;
import br.com.fiap.workshop_management_system.registration.customer.application.exception.CustomerNotFoundException;
import br.com.fiap.workshop_management_system.registration.customer.application.exception
        .CustomerTaxIdAlreadyExistsException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = CustomerController.class)
class CustomerExceptionHandler {

    @ExceptionHandler(CustomerNotFoundException.class)
    ResponseEntity<ErrorResponse> handleNotFound(CustomerNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("CUSTOMER_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(CustomerTaxIdAlreadyExistsException.class)
    ResponseEntity<ErrorResponse> handleDuplicateTaxId(CustomerTaxIdAlreadyExistsException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CUSTOMER_TAX_ID_ALREADY_EXISTS", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> handleInvalidContract() {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_ERROR", "Requisição inválida"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> handleInvalidCustomer(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_CUSTOMER", exception.getMessage()));
    }
}
