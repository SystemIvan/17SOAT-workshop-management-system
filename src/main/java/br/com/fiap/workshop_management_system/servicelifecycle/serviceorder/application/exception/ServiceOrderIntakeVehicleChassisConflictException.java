package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception;

public class ServiceOrderIntakeVehicleChassisConflictException extends RuntimeException {

    public ServiceOrderIntakeVehicleChassisConflictException() {
        super("Já existe um veículo com este chassi");
    }
}
