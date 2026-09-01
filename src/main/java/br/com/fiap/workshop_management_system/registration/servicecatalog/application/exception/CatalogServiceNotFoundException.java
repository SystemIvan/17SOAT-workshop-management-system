package br.com.fiap.workshop_management_system.registration.servicecatalog.application.exception;

public class CatalogServiceNotFoundException extends RuntimeException {

    public CatalogServiceNotFoundException() {
        super("Serviço não encontrado no catálogo");
    }
}
