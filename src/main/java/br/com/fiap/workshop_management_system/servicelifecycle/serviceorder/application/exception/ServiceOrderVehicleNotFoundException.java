package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception;

public class ServiceOrderVehicleNotFoundException extends RuntimeException {

    public ServiceOrderVehicleNotFoundException() {
        super("Veículo não encontrado");
    }
}
