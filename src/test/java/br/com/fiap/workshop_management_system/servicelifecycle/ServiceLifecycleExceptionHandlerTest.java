package br.com.fiap.workshop_management_system.servicelifecycle;

import br.com.fiap.workshop_management_system.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceLifecycleExceptionHandlerTest {

    private final ServiceLifecycleExceptionHandler handler = new ServiceLifecycleExceptionHandler();

    @Test
    void mapsIllegalStateExceptionToConflictWithStableCode() {
        ResponseEntity<ErrorResponse> response = handler.handleInvalidState(
                new IllegalStateException("Cannot assign a technician to a ServiceExecution in status COMPLETED"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("INVALID_STATE_TRANSITION", response.getBody().code());
        assertEquals("Cannot assign a technician to a ServiceExecution in status COMPLETED",
                response.getBody().message());
    }
}
