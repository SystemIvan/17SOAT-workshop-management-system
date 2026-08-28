package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model;

public class InvalidExecutionTimePeriodException extends RuntimeException {

    public InvalidExecutionTimePeriodException() {
        super("Execution time period requires from to be earlier than to");
    }
}
