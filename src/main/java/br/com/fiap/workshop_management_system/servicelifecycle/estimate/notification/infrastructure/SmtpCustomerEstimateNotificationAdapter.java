package br.com.fiap.workshop_management_system.servicelifecycle.estimate.notification.infrastructure;

import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.Estimate;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateLine;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.repository.EstimateRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.notification.application.port.CustomerEstimateNotificationPort;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.notification.CustomerEmail;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.notification.EmailMasking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RF52 - real e-mail channel for a generated Estimate, selected by {@code app.notification.email.channel=smtp}.
 * Sends the services with their values, the total and the validity so the Customer can decide. Until
 * {@code estimate-approval-link} exists the e-mail is informative only (functional-spec, "E-mail de orçamento",
 * rule 3): it never asks for a reply that nothing would process.
 *
 * <p>Commercial values go only to the Customer's e-mail, never to logs.
 */
@Component
@ConditionalOnProperty(name = "app.notification.email.channel", havingValue = "smtp")
public class SmtpCustomerEstimateNotificationAdapter implements CustomerEstimateNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(SmtpCustomerEstimateNotificationAdapter.class);

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final ZoneId WORKSHOP_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter VALIDITY_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", PT_BR).withZone(WORKSHOP_ZONE);

    private final JavaMailSender mailSender;
    private final EstimateRepository estimateRepository;
    private final CustomerRepository customerRepository;
    private final String from;

    public SmtpCustomerEstimateNotificationAdapter(
            JavaMailSender mailSender,
            EstimateRepository estimateRepository,
            CustomerRepository customerRepository,
            @Value("${app.notification.email.from}") String from) {
        this.mailSender = mailSender;
        this.estimateRepository = estimateRepository;
        this.customerRepository = customerRepository;
        this.from = from;
    }

    @Override
    public void notifyEstimateGenerated(UUID estimateId, UUID serviceOrderId, UUID customerId, Instant expiresAt) {
        Optional<Estimate> estimate = estimateRepository.findById(estimateId);
        CustomerEmail email = customerRepository.findById(customerId)
                .map(CustomerEmail::of)
                .orElse(new CustomerEmail(null));
        if (estimate.isEmpty() || !email.isPresent()) {
            log.warn("Cannot notify customer {} about generated estimate {} of service order {}: "
                    + "estimate or customer e-mail not found", customerId, estimateId, serviceOrderId);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email.value());
        message.setSubject("Orçamento da OS " + serviceOrderId + " aguardando sua aprovação");
        message.setText(body(estimate.get(), expiresAt));
        mailSender.send(message);
        log.info("E-mail sent | to={} | customerId={} | estimateId={} | serviceOrderId={}",
                EmailMasking.mask(email.value()), customerId, estimateId, serviceOrderId);
    }

    /**
     * Single place where the e-mail body is assembled; {@code estimate-approval-link} extends it with the
     * approve/reject links.
     */
    String body(Estimate estimate, Instant expiresAt) {
        String lines = estimate.lines().stream()
                .map(this::line)
                .collect(Collectors.joining("\n"));
        return """
                Olá,

                O orçamento da sua ordem de serviço está pronto e aguarda sua aprovação.

                Ordem de serviço: %s
                Orçamento: %s

                Serviços:
                %s

                Total: %s
                %s

                Para aprovar ou recusar o orçamento, entre em contato com a oficina.

                Esta é uma mensagem automática.
                """.formatted(
                estimate.serviceOrderId(),
                estimate.id(),
                lines,
                format(estimate.total()),
                validity(expiresAt));
    }

    private String line(EstimateLine line) {
        return "- " + line.serviceName() + ": " + format(line.lineTotal());
    }

    private static String validity(Instant expiresAt) {
        if (expiresAt == null) {
            return "Validade: não definida";
        }
        return "Válido até: " + VALIDITY_FORMAT.format(expiresAt) + " (horário de Brasília)";
    }

    static String format(Money money) {
        DecimalFormat decimal = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(PT_BR));
        decimal.setRoundingMode(RoundingMode.HALF_EVEN);
        String prefix = "BRL".equals(money.currency()) ? "R$ " : money.currency() + " ";
        return prefix + decimal.format(money.value());
    }
}
