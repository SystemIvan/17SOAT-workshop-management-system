package br.com.fiap.workshop_management_system.registration.vehicle.application.exception;

public class VehicleNotFoundException extends RuntimeException {

    public VehicleNotFoundException() {
        super("Veículo não encontrado");
    }
}
