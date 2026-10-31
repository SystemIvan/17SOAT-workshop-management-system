package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception;

public class ServiceOrderIntakeVehicleOwnershipConflictException extends RuntimeException {

    public ServiceOrderIntakeVehicleOwnershipConflictException() {
        super("A placa informada pertence a veículo de outro cliente");
    }
}
