package br.com.fiap.workshop_management_system.servicelifecycle;

import br.com.fiap.workshop_management_system.ErrorResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .CatalogServiceArchivedForNewWorkException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .CatalogServiceNotFoundForNewWorkException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderIntakeConcurrentRegistrationException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderIntakeCustomerArchivedException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderIntakeInvalidDataException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderIntakeVehicleChassisConflictException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderIntakeVehicleOwnershipConflictException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderStockItemInactiveException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderStockItemNotFoundException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderVehicleArchivedException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderVehicleNotFoundException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.InvalidServiceOrderException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model
        .InvalidExecutionTimePeriodException;
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

    @ExceptionHandler(InvalidExecutionTimePeriodException.class)
    ResponseEntity<ErrorResponse> handleInvalidExecutionTimePeriod(InvalidExecutionTimePeriodException exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_EXECUTION_TIME_PERIOD", exception.getMessage()));
    }

    @ExceptionHandler(CatalogServiceNotFoundForNewWorkException.class)
    ResponseEntity<ErrorResponse> handleCatalogServiceNotFound(CatalogServiceNotFoundForNewWorkException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("CATALOG_SERVICE_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(CatalogServiceArchivedForNewWorkException.class)
    ResponseEntity<ErrorResponse> handleCatalogServiceArchived(CatalogServiceArchivedForNewWorkException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CATALOG_SERVICE_ARCHIVED", ex.getMessage()));
    }

    @ExceptionHandler(ServiceOrderVehicleNotFoundException.class)
    ResponseEntity<ErrorResponse> handleVehicleNotFound(ServiceOrderVehicleNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("VEHICLE_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(ServiceOrderVehicleArchivedException.class)
    ResponseEntity<ErrorResponse> handleVehicleArchived(ServiceOrderVehicleArchivedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("VEHICLE_ARCHIVED", exception.getMessage()));
    }

    @ExceptionHandler(ServiceOrderStockItemNotFoundException.class)
    ResponseEntity<ErrorResponse> handleStockItemNotFound(ServiceOrderStockItemNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("STOCK_ITEM_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(ServiceOrderStockItemInactiveException.class)
    ResponseEntity<ErrorResponse> handleStockItemInactive(ServiceOrderStockItemInactiveException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("STOCK_ITEM_INACTIVE", exception.getMessage()));
    }

    @ExceptionHandler(ServiceOrderIntakeInvalidDataException.class)
    ResponseEntity<ErrorResponse> handleIntakeInvalidData(ServiceOrderIntakeInvalidDataException exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_ERROR", exception.getMessage()));
    }

    @ExceptionHandler(ServiceOrderIntakeCustomerArchivedException.class)
    ResponseEntity<ErrorResponse> handleIntakeCustomerArchived(ServiceOrderIntakeCustomerArchivedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CUSTOMER_ARCHIVED", exception.getMessage()));
    }

    @ExceptionHandler(ServiceOrderIntakeVehicleOwnershipConflictException.class)
    ResponseEntity<ErrorResponse> handleIntakeVehicleOwnership(
            ServiceOrderIntakeVehicleOwnershipConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("VEHICLE_OWNED_BY_ANOTHER_CUSTOMER", exception.getMessage()));
    }

    @ExceptionHandler(ServiceOrderIntakeVehicleChassisConflictException.class)
    ResponseEntity<ErrorResponse> handleIntakeVehicleChassis(
            ServiceOrderIntakeVehicleChassisConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("VEHICLE_CHASSIS_ALREADY_EXISTS", exception.getMessage()));
    }

    @ExceptionHandler(ServiceOrderIntakeConcurrentRegistrationException.class)
    ResponseEntity<ErrorResponse> handleIntakeConcurrentRegistration(
            ServiceOrderIntakeConcurrentRegistrationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CONCURRENT_REGISTRATION", exception.getMessage()));
    }

    @ExceptionHandler(InvalidServiceOrderException.class)
    ResponseEntity<ErrorResponse> handleInvalidServiceOrder(InvalidServiceOrderException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ErrorResponse> handleInvalidState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("INVALID_STATE_TRANSITION", ex.getMessage()));
    }
}
