package br.com.fiap.workshop_management_system.identity.auth.domain.model;

/**
 * Signals an invalid UserAccount command argument, mirroring the pattern used by other aggregates
 * (e.g. InvalidServiceOrderException) instead of a bare IllegalArgumentException.
 */
public class InvalidUserAccountException extends IllegalArgumentException {

    public InvalidUserAccountException(String message) {
        super(message);
    }
}
