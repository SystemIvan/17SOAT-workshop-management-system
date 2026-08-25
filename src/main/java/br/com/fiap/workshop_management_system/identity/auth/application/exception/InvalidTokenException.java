package br.com.fiap.workshop_management_system.identity.auth.application.exception;

public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(Throwable cause) {
        super("Invalid or expired token", cause);
    }
}
