package br.com.fiap.workshop_management_system.identity.auth.application.exception;

public class DuplicateUsernameException extends RuntimeException {

    public DuplicateUsernameException() {
        super("A username is already registered for another account");
    }
}
