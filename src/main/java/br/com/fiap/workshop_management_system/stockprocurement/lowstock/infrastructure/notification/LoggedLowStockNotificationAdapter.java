package br.com.fiap.workshop_management_system.stockprocurement.lowstock.infrastructure.notification;

import br.com.fiap.workshop_management_system.stockprocurement.lowstock.application.event.LowStockDetectedEvent;
import br.com.fiap.workshop_management_system.stockprocurement.lowstock.application.port.LowStockNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggedLowStockNotificationAdapter implements LowStockNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(LoggedLowStockNotificationAdapter.class);

    @Override
    public void notifyLowStockDetected(LowStockDetectedEvent event) {
        log.info("Low stock detected | occurrenceId={} | stockItemId={} | availableQuantity={} | minimumQuantity={} "
                        + "| targetQuantity={} | suggestedQuantity={}",
                event.occurrenceId(), event.stockItemId(), event.observedAvailableQuantity(), event.minimumQuantity(),
                event.targetQuantity(), event.suggestedQuantity());
    }
}
