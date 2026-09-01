package br.com.fiap.workshop_management_system.registration.customer.application.exception;

public class CustomerTaxIdAlreadyExistsException extends RuntimeException {

    public CustomerTaxIdAlreadyExistsException() {
        super("Já existe um cliente com este número de identificação fiscal");
    }
}
