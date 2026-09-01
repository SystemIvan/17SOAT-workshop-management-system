package br.com.fiap.workshop_management_system.registration.customer.domain.model;

public class CustomerArchivedException extends IllegalStateException {

    public CustomerArchivedException() {
        super("Cliente arquivado não pode ser alterado");
    }
}
