package br.com.fiap.workshop_management_system.servicelifecycle.estimate.notification.infrastructure;

import br.com.fiap.workshop_management_system.registration.customer.domain.model.ContactInfo;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SimulatedEmailCustomerEstimateNotificationAdapterTest {

    private static final String RAW_EMAIL = "jane.doe@example.com";
    private static final String RAW_NAME = "Jane Doe";

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final SimulatedEmailCustomerEstimateNotificationAdapter adapter =
            new SimulatedEmailCustomerEstimateNotificationAdapter(customerRepository);

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        appender = new ListAppender<>();
        appender.start();
        logger().addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger().detachAppender(appender);
    }

    private Logger logger() {
        return (Logger) LoggerFactory.getLogger(SimulatedEmailCustomerEstimateNotificationAdapter.class);
    }

    @Test
    void logsSimulatedEmailWithExpiresAtAndWithoutRawContactDataWhenCustomerIsFound() {
        UUID estimateId = UUID.randomUUID();
        UUID serviceOrderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
        Customer customer = Customer.create(RAW_NAME, new TaxId("52998224725"), new ContactInfo(RAW_EMAIL, "11999999999"));
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

        adapter.notifyEstimateGenerated(estimateId, serviceOrderId, customerId, expiresAt);

        assertEquals(1, appender.list.size());
        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.INFO, event.getLevel());
        String message = event.getFormattedMessage();
        assertFalse(message.contains(RAW_EMAIL), "log must not contain the raw e-mail address");
        assertFalse(message.contains(RAW_NAME), "log must not contain the raw customer name");
        assertTrue(message.contains(estimateId.toString()));
        assertTrue(message.contains(serviceOrderId.toString()));
        assertTrue(message.contains(customerId.toString()));
        assertTrue(message.contains(expiresAt.toString()), "log must state expiresAt as received, unmodified");
    }

    @Test
    void logsWarningAndDoesNotThrowWhenCustomerIsNotFound() {
        UUID estimateId = UUID.randomUUID();
        UUID serviceOrderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
        when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

        adapter.notifyEstimateGenerated(estimateId, serviceOrderId, customerId, expiresAt);

        assertEquals(1, appender.list.size());
        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.WARN, event.getLevel());
        String message = event.getFormattedMessage();
        assertTrue(message.contains(estimateId.toString()));
        assertTrue(message.contains(customerId.toString()));
    }
}
