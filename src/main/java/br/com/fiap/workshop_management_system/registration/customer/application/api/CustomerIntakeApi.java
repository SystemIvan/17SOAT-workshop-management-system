package br.com.fiap.workshop_management_system.registration.customer.application.api;

/**
 * Identifies an active Customer by tax ID or registers a new one inside the caller's transaction.
 *
 * <p>Invalid or missing data for a new Customer is reported with {@link IllegalArgumentException}. A concurrent
 * registration of the same tax ID is reported with {@link CustomerIntakeConflictException}.
 */
public interface CustomerIntakeApi {

    CustomerIntakeResult identifyOrRegister(CustomerIntakeCommand command);
}
