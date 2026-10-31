package br.com.fiap.workshop_management_system.servicelifecycle.estimate.notification.infrastructure;

import br.com.fiap.workshop_management_system.registration.customer.domain.model.ContactInfo;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.Estimate;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateLine;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateStockItem;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.repository.EstimateRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockItemType;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SmtpCustomerEstimateNotificationAdapterTest {

    private static final String RAW_EMAIL = "jane.doe@example.com";
    private static final String FROM = "no-reply@workshop.local";
    // 15:00 UTC = 12:00 in America/Sao_Paulo
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-18T15:00:00Z");

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final EstimateRepository estimateRepository = mock(EstimateRepository.class);
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final SmtpCustomerEstimateNotificationAdapter adapter =
            new SmtpCustomerEstimateNotificationAdapter(mailSender, estimateRepository, customerRepository, FROM);

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
        return (Logger) LoggerFactory.getLogger(SmtpCustomerEstimateNotificationAdapter.class);
    }

    @Test
    void sendsTheEstimateWithServicesValuesTotalAndValidity() {
        Estimate estimate = estimate();
        UUID customerId = estimate.customerId();
        when(estimateRepository.findById(estimate.id())).thenReturn(Optional.of(estimate));
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer()));

        adapter.notifyEstimateGenerated(estimate.id(), estimate.serviceOrderId(), customerId, EXPIRES_AT);

        SimpleMailMessage message = sentMessage();
        assertEquals(FROM, message.getFrom());
        assertArrayEquals(new String[] {RAW_EMAIL}, message.getTo());
        assertEquals("Orçamento da OS " + estimate.serviceOrderId() + " aguardando sua aprovação",
                message.getSubject());
        String text = message.getText();
        assertTrue(text.contains("Ordem de serviço: " + estimate.serviceOrderId()));
        assertTrue(text.contains("Orçamento: " + estimate.id()));
        assertTrue(text.contains("- Troca de oleo: R$ 191,80"), text);
        assertTrue(text.contains("- Alinhamento: R$ 80,00"), text);
        assertTrue(text.contains("Total: R$ 271,80"), text);
        assertTrue(text.contains("Válido até: 18/08/2026 às 12:00 (horário de Brasília)"), text);
    }

    @Test
    void neverAsksTheCustomerToReplyToTheEmail() {
        Estimate estimate = estimate();
        when(estimateRepository.findById(estimate.id())).thenReturn(Optional.of(estimate));
        when(customerRepository.findById(estimate.customerId())).thenReturn(Optional.of(customer()));

        adapter.notifyEstimateGenerated(estimate.id(), estimate.serviceOrderId(), estimate.customerId(), EXPIRES_AT);

        SimpleMailMessage message = sentMessage();
        assertEquals(null, message.getReplyTo());
        assertFalse(message.getText().contains("APROVO"));
        assertFalse(message.getText().toLowerCase().contains("responda"));
        assertTrue(message.getText().contains("entre em contato com a oficina"));
    }

    @Test
    void statesThatValidityIsUndefinedWhenThereIsNoExpiration() {
        Estimate estimate = estimate();
        when(estimateRepository.findById(estimate.id())).thenReturn(Optional.of(estimate));
        when(customerRepository.findById(estimate.customerId())).thenReturn(Optional.of(customer()));

        adapter.notifyEstimateGenerated(estimate.id(), estimate.serviceOrderId(), estimate.customerId(), null);

        assertTrue(sentMessage().getText().contains("Validade: não definida"));
    }

    @Test
    void logsTheSentEmailWithoutValuesOrRawAddress() {
        Estimate estimate = estimate();
        when(estimateRepository.findById(estimate.id())).thenReturn(Optional.of(estimate));
        when(customerRepository.findById(estimate.customerId())).thenReturn(Optional.of(customer()));

        adapter.notifyEstimateGenerated(estimate.id(), estimate.serviceOrderId(), estimate.customerId(), EXPIRES_AT);

        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.INFO, event.getLevel());
        String message = event.getFormattedMessage();
        assertTrue(message.contains(estimate.id().toString()));
        assertTrue(message.contains("j***@e***"));
        assertFalse(message.contains(RAW_EMAIL));
        assertFalse(message.contains("271,80"), "commercial values must not be logged");
    }

    @Test
    void doesNotSendAndWarnsWithIdsWhenTheEstimateIsNotFound() {
        UUID estimateId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(estimateRepository.findById(estimateId)).thenReturn(Optional.empty());
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer()));

        adapter.notifyEstimateGenerated(estimateId, UUID.randomUUID(), customerId, EXPIRES_AT);

        verifyNoInteractions(mailSender);
        assertWarnedWithIdsOnly(estimateId, customerId);
    }

    @Test
    void doesNotSendAndWarnsWithIdsWhenTheCustomerIsNotFound() {
        Estimate estimate = estimate();
        when(estimateRepository.findById(estimate.id())).thenReturn(Optional.of(estimate));
        when(customerRepository.findById(estimate.customerId())).thenReturn(Optional.empty());

        adapter.notifyEstimateGenerated(estimate.id(), estimate.serviceOrderId(), estimate.customerId(), EXPIRES_AT);

        verifyNoInteractions(mailSender);
        assertWarnedWithIdsOnly(estimate.id(), estimate.customerId());
    }

    @Test
    void formatsMoneyInBrazilianPortuguese() {
        assertEquals("R$ 1.234,50", SmtpCustomerEstimateNotificationAdapter.format(
                Money.brl(new BigDecimal("1234.5"))));
        assertEquals("R$ 0,00", SmtpCustomerEstimateNotificationAdapter.format(Money.brl(BigDecimal.ZERO)));
        assertEquals("USD 10,00", SmtpCustomerEstimateNotificationAdapter.format(
                new Money(BigDecimal.TEN, "USD")));
    }

    private void assertWarnedWithIdsOnly(UUID estimateId, UUID customerId) {
        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.WARN, event.getLevel());
        assertTrue(event.getFormattedMessage().contains(estimateId.toString()));
        assertTrue(event.getFormattedMessage().contains(customerId.toString()));
        assertFalse(event.getFormattedMessage().contains(RAW_EMAIL));
    }

    private SimpleMailMessage sentMessage() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private Estimate estimate() {
        EstimateStockItem filter = new EstimateStockItem(
                UUID.randomUUID(), StockItemType.PART, 2, "Filtro de oleo", Money.brl(new BigDecimal("35.90")));
        EstimateLine oilChange = new EstimateLine(
                UUID.randomUUID(), "Troca de oleo", Money.brl(new BigDecimal("120.00")), List.of(filter));
        EstimateLine alignment = new EstimateLine(
                UUID.randomUUID(), "Alinhamento", Money.brl(new BigDecimal("80.00")), List.of());
        return Estimate.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now(), EXPIRES_AT,
                List.of(oilChange, alignment));
    }

    private Customer customer() {
        return Customer.create("Jane Doe", new TaxId("52998224725"), new ContactInfo(RAW_EMAIL, "11999999999"));
    }
}
