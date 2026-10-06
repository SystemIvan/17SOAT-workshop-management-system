package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.notification;

import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderStatusLabel;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.CustomerNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * RF52 - real e-mail channel for Service Order status changes, selected by
 * {@code app.notification.email.channel=smtp}. Requires a configured {@code spring.mail.host}; without it there is
 * no {@link JavaMailSender} bean and startup fails fast instead of silently dropping e-mails.
 *
 * <p>{@link org.springframework.mail.MailException} is not caught here: it reaches
 * {@code ServiceOrderStatusChangedNotificationListener}, which logs it with opaque IDs only.
 */
@Component
@ConditionalOnProperty(name = "app.notification.email.channel", havingValue = "smtp")
public class SmtpCustomerNotificationAdapter implements CustomerNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(SmtpCustomerNotificationAdapter.class);

    private final JavaMailSender mailSender;
    private final CustomerRepository customerRepository;
    private final String from;

    public SmtpCustomerNotificationAdapter(
            JavaMailSender mailSender,
            CustomerRepository customerRepository,
            @Value("${app.notification.email.from}") String from) {
        this.mailSender = mailSender;
        this.customerRepository = customerRepository;
        this.from = from;
    }

    @Override
    public void notifyServiceOrderStatusChanged(
            UUID serviceOrderId, UUID customerId, ServiceOrderStatusLabel newStatus) {
        CustomerEmail email = customerRepository.findById(customerId)
                .map(CustomerEmail::of)
                .orElse(new CustomerEmail(null));
        if (!email.isPresent()) {
            log.warn("Cannot notify customer {} about service order {} status change: "
                    + "customer not found or without e-mail", customerId, serviceOrderId);
            return;
        }
        mailSender.send(message(serviceOrderId, newStatus, email.value()));
        log.info("E-mail sent | to={} | customerId={} | serviceOrderId={} | newStatus={}",
                EmailMasking.mask(email.value()), customerId, serviceOrderId, newStatus);
    }

    private SimpleMailMessage message(UUID serviceOrderId, ServiceOrderStatusLabel newStatus, String to) {
        String statusName = displayName(newStatus);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("OS " + serviceOrderId + ": status atualizado para " + statusName);
        message.setText("""
                Olá,

                O status da sua ordem de serviço foi atualizado.

                Ordem de serviço: %s
                Novo status: %s

                Esta é uma mensagem automática. Em caso de dúvidas, entre em contato com a oficina.
                """.formatted(serviceOrderId, statusName));
        return message;
    }

    /**
     * Human-readable RF39 names. Kept local to the e-mail: the enum constants are part of the JSON contract of the
     * status endpoints and must not change.
     */
    static String displayName(ServiceOrderStatusLabel label) {
        return switch (label) {
            case RECEBIDA -> "Recebida";
            case DIAGNOSTICO -> "Diagnóstico";
            case AGUARDANDO_APROVACAO -> "Aguardando Aprovação";
            case EXECUCAO -> "Execução";
            case FINALIZADA -> "Finalizada";
            case ENTREGUE -> "Entregue";
        };
    }
}
