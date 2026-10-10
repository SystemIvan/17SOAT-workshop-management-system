package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.notification;

import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderStatusLabel;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.CustomerNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Simulated e-mail channel (channel decision recorded in technical-spec.md): writes a
 * structured log line instead of sending a real e-mail. The log line never contains the
 * raw customer e-mail/name (AGENTS.md: no personal data in logs) - only opaque IDs and a
 * masked e-mail for demo traceability.
 *
 * <p>RF52: active for {@code app.notification.email.channel=log}, the default; {@code smtp} selects
 * {@link SmtpCustomerNotificationAdapter} instead.
 */
@Component
@ConditionalOnProperty(name = "app.notification.email.channel", havingValue = "log", matchIfMissing = true)
public class SimulatedEmailCustomerNotificationAdapter implements CustomerNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(SimulatedEmailCustomerNotificationAdapter.class);

    private final CustomerRepository customerRepository;

    public SimulatedEmailCustomerNotificationAdapter(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public void notifyServiceOrderStatusChanged(
            UUID serviceOrderId, UUID customerId, ServiceOrderStatusLabel newStatus) {
        customerRepository.findById(customerId)
                .map(CustomerEmail::of)
                .filter(CustomerEmail::isPresent)
                .ifPresentOrElse(
                        email -> logSimulatedEmail(serviceOrderId, customerId, newStatus, email.value()),
                        () -> log.warn("Cannot notify customer {} about service order {} status change: "
                                + "customer not found or without e-mail", customerId, serviceOrderId));
    }

    private void logSimulatedEmail(
            UUID serviceOrderId, UUID customerId, ServiceOrderStatusLabel newStatus, String email) {
        log.info("Simulated e-mail sent | to={} | customerId={} | subject=\"Service order status changed\" "
                        + "| serviceOrderId={} | newStatus={}",
                EmailMasking.mask(email), customerId, serviceOrderId, newStatus);
    }
}
