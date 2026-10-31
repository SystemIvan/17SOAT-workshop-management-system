package br.com.fiap.workshop_management_system.registration.customer.application.api;

public class CustomerIntakeConflictException extends RuntimeException {

    public CustomerIntakeConflictException() {
        super("O cliente foi cadastrado simultaneamente por outra operação");
    }
}
