package br.com.fiap.workshop_management_system.identity.auth.infrastructure.web;

import br.com.fiap.workshop_management_system.ErrorResponse;
import br.com.fiap.workshop_management_system.identity.auth.application.exception.DuplicateUsernameException;
import br.com.fiap.workshop_management_system.identity.auth.application.exception.InvalidCredentialsException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AuthController.class)
class AuthExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("INVALID_CREDENTIALS", exception.getMessage()));
    }

    @ExceptionHandler(DuplicateUsernameException.class)
    ResponseEntity<ErrorResponse> handleDuplicateUsername(DuplicateUsernameException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("USERNAME_ALREADY_EXISTS", exception.getMessage()));
    }
}
