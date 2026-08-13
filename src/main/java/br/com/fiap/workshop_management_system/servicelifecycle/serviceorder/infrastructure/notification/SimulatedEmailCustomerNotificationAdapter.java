package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.notification;

import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.CustomerNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Simulated e-mail channel (channel decision recorded in technical-spec.md): writes a
 * structured log line instead of sending a real e-mail. The log line never contains the
 * raw customer e-mail/name (AGENTS.md: no personal data in logs) - only opaque IDs and a
 * masked e-mail for demo traceability.
 */
@Component
public class SimulatedEmailCustomerNotificationAdapter implements CustomerNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(SimulatedEmailCustomerNotificationAdapter.class);

    private final CustomerRepository customerRepository;

    public SimulatedEmailCustomerNotificationAdapter(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public void notifyServiceOrderFinalized(UUID serviceOrderId, UUID customerId) {
        customerRepository.findById(customerId).ifPresentOrElse(
                customer -> logSimulatedEmail(serviceOrderId, customerId, customer),
                () -> log.warn("Cannot notify customer {} about finalized service order {}: customer not found",
                        customerId, serviceOrderId));
    }

    private void logSimulatedEmail(UUID serviceOrderId, UUID customerId, Customer customer) {
        String maskedEmail = maskEmail(customer.contactInfo().email());
        log.info("Simulated e-mail sent | to={} | customerId={} | subject=\"Your vehicle is ready for pickup\" "
                        + "| serviceOrderId={}",
                maskedEmail, customerId, serviceOrderId);
    }

    private static String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return "***";
        }
        String maskedLocal = email.charAt(0) + "***";
        String domain = email.substring(atIndex + 1);
        String maskedDomain = domain.isEmpty() ? "***" : domain.charAt(0) + "***";
        return maskedLocal + "@" + maskedDomain;
    }
}
