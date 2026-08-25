package br.com.fiap.workshop_management_system.registration.servicecatalog.application.exception;

import java.util.UUID;

public class CatalogServiceNameAlreadyExistsException extends RuntimeException {

    private final UUID existingId;
    private final String existingName;

    public CatalogServiceNameAlreadyExistsException(UUID existingId, String existingName) {
        super("Já existe um serviço cadastrado com esse nome: " + existingId + " - " + existingName);
        this.existingId = existingId;
        this.existingName = existingName;
    }

    public UUID existingId() {
        return existingId;
    }

    public String existingName() {
        return existingName;
    }
}
