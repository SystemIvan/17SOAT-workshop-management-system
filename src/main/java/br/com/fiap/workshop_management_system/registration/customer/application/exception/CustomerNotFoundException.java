package br.com.fiap.workshop_management_system.registration.customer.application.exception;

public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException() {
        super("Cliente não encontrado");
    }
}
