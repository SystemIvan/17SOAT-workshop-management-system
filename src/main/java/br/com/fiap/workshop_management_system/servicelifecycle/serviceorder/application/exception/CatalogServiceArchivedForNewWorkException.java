package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception;

public class CatalogServiceArchivedForNewWorkException extends RuntimeException {

    public CatalogServiceArchivedForNewWorkException() {
        super("Serviço arquivado não pode ser utilizado em novos trabalhos");
    }
}
