package br.com.fiap.workshop_management_system.servicelifecycle;

import br.com.fiap.workshop_management_system.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Scoped to {@code servicelifecycle} controllers only, so an unrelated {@link IllegalStateException} from
 * technical/infrastructure code elsewhere in the application is never miscategorized as a business
 * conflict and does not leak its message as a stable {@code 409}. Domain code in {@code serviceorder} and
 * {@code technician} intentionally throws plain {@link IllegalStateException} for state-transition
 * conflicts (RF19-RF22, Technician availability); this advice translates only those.
 */
@RestControllerAdvice(basePackages = "br.com.fiap.workshop_management_system.servicelifecycle")
class ServiceLifecycleExceptionHandler {

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ErrorResponse> handleInvalidState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("INVALID_STATE_TRANSITION", ex.getMessage()));
    }
}
