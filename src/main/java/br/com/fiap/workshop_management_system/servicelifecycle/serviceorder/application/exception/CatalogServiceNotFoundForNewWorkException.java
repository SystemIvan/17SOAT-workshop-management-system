package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception;

public class CatalogServiceNotFoundForNewWorkException extends RuntimeException {

    public CatalogServiceNotFoundForNewWorkException() {
        super("Serviço não encontrado no catálogo");
    }
}
