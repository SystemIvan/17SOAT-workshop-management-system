package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception;

public class ServiceOrderIntakeConcurrentRegistrationException extends RuntimeException {

    public ServiceOrderIntakeConcurrentRegistrationException() {
        super("O cadastro foi alterado simultaneamente por outra operação; tente novamente");
    }
}
