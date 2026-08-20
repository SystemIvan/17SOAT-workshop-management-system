package br.com.fiap.workshop_management_system.servicelifecycle.estimate.notification.infrastructure;

import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.notification.application.port.CustomerEstimateNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Simulated e-mail channel, reusing the channel decision recorded for notifications-so-finalized
 * (technical-spec.md): writes a structured log line instead of sending a real e-mail. The log line
 * never contains the raw customer e-mail/name (AGENTS.md: no personal data in logs) - only opaque IDs,
 * expiresAt as received, and a masked e-mail for demo traceability.
 */
@Component
public class SimulatedEmailCustomerEstimateNotificationAdapter implements CustomerEstimateNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(SimulatedEmailCustomerEstimateNotificationAdapter.class);

    private final CustomerRepository customerRepository;

    public SimulatedEmailCustomerEstimateNotificationAdapter(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public void notifyEstimateGenerated(UUID estimateId, UUID serviceOrderId, UUID customerId, Instant expiresAt) {
        customerRepository.findById(customerId).ifPresentOrElse(
                customer -> logSimulatedEmail(estimateId, serviceOrderId, customerId, expiresAt, customer),
                () -> log.warn("Cannot notify customer {} about generated estimate {}: customer not found",
                        customerId, estimateId));
    }

    private void logSimulatedEmail(
            UUID estimateId, UUID serviceOrderId, UUID customerId, Instant expiresAt, Customer customer) {
        String maskedEmail = maskEmail(customer.contactInfo().email());
        log.info("Simulated e-mail sent | to={} | customerId={} | subject=\"Your estimate is awaiting approval\" "
                        + "| estimateId={} | serviceOrderId={} | expiresAt={}",
                maskedEmail, customerId, estimateId, serviceOrderId, expiresAt);
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
