package br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model;

public class CatalogServiceArchivedException extends IllegalStateException {

    public CatalogServiceArchivedException() {
        super("Serviço arquivado não pode ser atualizado");
    }
}
