package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.notification;

import br.com.fiap.workshop_management_system.registration.customer.domain.model.ContactInfo;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderStatusLabel;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SmtpCustomerNotificationAdapterTest {

    private static final String RAW_EMAIL = "jane.doe@example.com";
    private static final String RAW_NAME = "Jane Doe";
    private static final String FROM = "no-reply@workshop.local";

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final SmtpCustomerNotificationAdapter adapter =
            new SmtpCustomerNotificationAdapter(mailSender, customerRepository, FROM);

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
        return (Logger) LoggerFactory.getLogger(SmtpCustomerNotificationAdapter.class);
    }

    @Test
    void sendsAPlainTextEmailWithTheServiceOrderAndTheReadableNewStatus() {
        UUID serviceOrderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer()));

        adapter.notifyServiceOrderStatusChanged(
                serviceOrderId, customerId, ServiceOrderStatusLabel.AGUARDANDO_APROVACAO);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertEquals(FROM, message.getFrom());
        assertArrayEquals(new String[] {RAW_EMAIL}, message.getTo());
        assertEquals("OS " + serviceOrderId + ": status atualizado para Aguardando Aprovação",
                message.getSubject());
        assertTrue(message.getText().contains("Ordem de serviço: " + serviceOrderId));
        assertTrue(message.getText().contains("Novo status: Aguardando Aprovação"));
        assertFalse(message.getText().contains("AGUARDANDO_APROVACAO"), "internal constant must not leak");
    }

    @Test
    void logsTheSentEmailWithMaskedAddressAndIdsOnly() {
        UUID serviceOrderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer()));

        adapter.notifyServiceOrderStatusChanged(serviceOrderId, customerId, ServiceOrderStatusLabel.EXECUCAO);

        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.INFO, event.getLevel());
        String message = event.getFormattedMessage();
        assertTrue(message.contains("j***@e***"));
        assertTrue(message.contains(serviceOrderId.toString()));
        assertTrue(message.contains(customerId.toString()));
        assertFalse(message.contains(RAW_EMAIL));
        assertFalse(message.contains(RAW_NAME));
    }

    @Test
    void doesNotSendAndWarnsWithIdsWhenCustomerIsNotFound() {
        UUID serviceOrderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

        adapter.notifyServiceOrderStatusChanged(serviceOrderId, customerId, ServiceOrderStatusLabel.ENTREGUE);

        verifyNoInteractions(mailSender);
        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.WARN, event.getLevel());
        assertTrue(event.getFormattedMessage().contains(serviceOrderId.toString()));
        assertTrue(event.getFormattedMessage().contains(customerId.toString()));
    }

    @Test
    void letsDeliveryFailuresReachTheListener() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer()));
        doThrow(new MailSendException("SMTP unavailable")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThrows(MailSendException.class, () -> adapter.notifyServiceOrderStatusChanged(
                UUID.randomUUID(), customerId, ServiceOrderStatusLabel.FINALIZADA));
    }

    @ParameterizedTest
    @EnumSource(ServiceOrderStatusLabel.class)
    void everyNominalStatusHasAReadableName(ServiceOrderStatusLabel label) {
        String name = SmtpCustomerNotificationAdapter.displayName(label);

        assertFalse(name.isBlank());
        assertFalse(name.contains("_"));
    }

    @Test
    void readableNamesMatchTheRf39Wording() {
        assertArrayEquals(
                new String[] {"Recebida", "Diagnóstico", "Aguardando Aprovação", "Execução", "Finalizada", "Entregue"},
                Arrays.stream(ServiceOrderStatusLabel.values())
                        .map(SmtpCustomerNotificationAdapter::displayName)
                        .toArray(String[]::new));
    }

    private Customer customer() {
        return Customer.create(RAW_NAME, new TaxId("52998224725"), new ContactInfo(RAW_EMAIL, "11999999999"));
    }
}
