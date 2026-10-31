package br.com.fiap.workshop_management_system.registration.vehicle.application.api;

public class VehicleIntakeConflictException extends RuntimeException {

    public VehicleIntakeConflictException() {
        super("O veículo foi cadastrado simultaneamente por outra operação");
    }
}
