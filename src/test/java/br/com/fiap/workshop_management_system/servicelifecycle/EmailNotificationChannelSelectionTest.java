package br.com.fiap.workshop_management_system.servicelifecycle;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.notification.application.port
        .CustomerEstimateNotificationPort;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.notification.infrastructure
        .SimulatedEmailCustomerEstimateNotificationAdapter;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.notification.infrastructure
        .SmtpCustomerEstimateNotificationAdapter;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.CustomerNotificationPort;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.notification
        .SimulatedEmailCustomerNotificationAdapter;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.notification
        .SmtpCustomerNotificationAdapter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * RF52 - {@code app.notification.email.channel} selects exactly one adapter per notification port.
 */
class EmailNotificationChannelSelectionTest {

    /**
     * {@code app.notification.email.channel=log}, the default of application.properties (and of the test suite).
     */
    @Nested
    @SpringBootTest
    class WithLogChannel {

        @Autowired
        private ApplicationContext context;

        @Test
        void usesTheSimulatedLogAdaptersAndCreatesNoMailSender() {
            assertInstanceOf(SimulatedEmailCustomerNotificationAdapter.class,
                    context.getBean(CustomerNotificationPort.class));
            assertInstanceOf(SimulatedEmailCustomerEstimateNotificationAdapter.class,
                    context.getBean(CustomerEstimateNotificationPort.class));
            assertEquals(0, context.getBeanNamesForType(JavaMailSender.class).length);
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "app.notification.email.channel=smtp",
            "spring.mail.host=localhost",
            "spring.mail.port=2525"
    })
    class WithSmtpChannel {

        @Autowired
        private ApplicationContext context;

        @Test
        void usesTheSmtpAdapters() {
            assertInstanceOf(SmtpCustomerNotificationAdapter.class, context.getBean(CustomerNotificationPort.class));
            assertInstanceOf(SmtpCustomerEstimateNotificationAdapter.class,
                    context.getBean(CustomerEstimateNotificationPort.class));
        }
    }
}
