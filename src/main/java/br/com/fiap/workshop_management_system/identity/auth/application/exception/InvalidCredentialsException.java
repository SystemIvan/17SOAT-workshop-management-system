package br.com.fiap.workshop_management_system.identity.auth.application.exception;

/**
 * Thrown for both an unknown username and a wrong password — never expose which one it was
 * (anti-enumeration, per technical-spec.md).
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
