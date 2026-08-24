package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception;

public class ServiceOrderVehicleArchivedException extends RuntimeException {

    public ServiceOrderVehicleArchivedException() {
        super("Veículo arquivado não está disponível para nova ordem de serviço");
    }
}
