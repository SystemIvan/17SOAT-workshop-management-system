package br.com.fiap.workshop_management_system.servicelifecycle;

import br.com.fiap.workshop_management_system.ErrorResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .CatalogServiceArchivedForNewWorkException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .CatalogServiceNotFoundForNewWorkException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderStockItemNotFoundException;
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

    @Test
    void mapsMissingCatalogServiceToNotFound() {
        ResponseEntity<ErrorResponse> response = handler.handleCatalogServiceNotFound(
                new CatalogServiceNotFoundForNewWorkException());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("CATALOG_SERVICE_NOT_FOUND", response.getBody().code());
        assertEquals("Serviço não encontrado no catálogo", response.getBody().message());
    }

    @Test
    void mapsArchivedCatalogServiceToConflict() {
        ResponseEntity<ErrorResponse> response = handler.handleCatalogServiceArchived(
                new CatalogServiceArchivedForNewWorkException());

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("CATALOG_SERVICE_ARCHIVED", response.getBody().code());
        assertEquals("Serviço arquivado não pode ser utilizado em novos trabalhos", response.getBody().message());
    }

    @Test
    void mapsMissingStockItemToNotFound() {
        ResponseEntity<ErrorResponse> response = handler.handleStockItemNotFound(
                new ServiceOrderStockItemNotFoundException());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("STOCK_ITEM_NOT_FOUND", response.getBody().code());
        assertEquals("Stock item was not found", response.getBody().message());
    }
}
