package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception;

public class ServiceOrderIntakeCustomerArchivedException extends RuntimeException {

    public ServiceOrderIntakeCustomerArchivedException() {
        super("Cliente arquivado não está disponível para nova ordem de serviço");
    }
}
