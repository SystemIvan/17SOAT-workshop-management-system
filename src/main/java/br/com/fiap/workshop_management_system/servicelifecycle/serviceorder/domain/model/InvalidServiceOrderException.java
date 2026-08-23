package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model;

/**
 * Signals an invalid Service Order command argument without conflating it with stock validation failures.
 */
public class InvalidServiceOrderException extends IllegalArgumentException {

    public InvalidServiceOrderException(String message) {
        super(message);
    }
}
