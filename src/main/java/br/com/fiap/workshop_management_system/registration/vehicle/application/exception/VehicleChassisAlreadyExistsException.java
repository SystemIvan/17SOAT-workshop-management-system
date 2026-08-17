package br.com.fiap.workshop_management_system.registration.vehicle.application.exception;

public class VehicleChassisAlreadyExistsException extends RuntimeException {

    public VehicleChassisAlreadyExistsException() {
        super("Já existe um veículo com este chassis");
    }
}
