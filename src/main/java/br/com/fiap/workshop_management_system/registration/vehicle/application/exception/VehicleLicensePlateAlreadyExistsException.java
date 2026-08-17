package br.com.fiap.workshop_management_system.registration.vehicle.application.exception;

public class VehicleLicensePlateAlreadyExistsException extends RuntimeException {

    public VehicleLicensePlateAlreadyExistsException() {
        super("Já existe um veículo com esta placa");
    }
}
