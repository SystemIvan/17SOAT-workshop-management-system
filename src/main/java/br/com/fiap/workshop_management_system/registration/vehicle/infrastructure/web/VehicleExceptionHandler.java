package br.com.fiap.workshop_management_system.registration.vehicle.infrastructure.web;

import br.com.fiap.workshop_management_system.ErrorResponse;
import br.com.fiap.workshop_management_system.registration.customer.application.exception.CustomerNotFoundException;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.CustomerArchivedException;
import br.com.fiap.workshop_management_system.registration.vehicle.application.exception
        .VehicleChassisAlreadyExistsException;
import br.com.fiap.workshop_management_system.registration.vehicle.application.exception
        .VehicleLicensePlateAlreadyExistsException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = VehicleController.class)
class VehicleExceptionHandler {

    @ExceptionHandler(CustomerNotFoundException.class)
    ResponseEntity<ErrorResponse> handleCustomerNotFound(CustomerNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("CUSTOMER_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(CustomerArchivedException.class)
    ResponseEntity<ErrorResponse> handleCustomerArchived() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "CUSTOMER_ARCHIVED",
                        "Cliente arquivado não está disponível para cadastrar veículo"));
    }

    @ExceptionHandler(VehicleLicensePlateAlreadyExistsException.class)
    ResponseEntity<ErrorResponse> handleDuplicateLicensePlate(VehicleLicensePlateAlreadyExistsException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("VEHICLE_LICENSE_PLATE_ALREADY_EXISTS", exception.getMessage()));
    }

    @ExceptionHandler(VehicleChassisAlreadyExistsException.class)
    ResponseEntity<ErrorResponse> handleDuplicateChassis(VehicleChassisAlreadyExistsException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("VEHICLE_CHASSIS_ALREADY_EXISTS", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> handleInvalidVehicle(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_VEHICLE", exception.getMessage()));
    }
}
