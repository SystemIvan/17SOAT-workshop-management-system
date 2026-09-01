package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.infrastructure.notification;

import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReserveStockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.StockReservationIssue;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.port.StockManagerNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class LoggedStockManagerNotificationAdapter implements StockManagerNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(LoggedStockManagerNotificationAdapter.class);

    @Override
    public void notifyStockReservationCreated(
            UUID reservationId,
            UUID serviceExecutionId,
            List<ReserveStockItem> items) {
        log.info("Stock reservation ready for separation | reservationId={} | serviceExecutionId={} | items={}",
                reservationId, serviceExecutionId, items);
    }

    @Override
    public void notifyStockReservationUnavailable(UUID serviceExecutionId, List<StockReservationIssue> issues) {
        log.info("Stock reservation unavailable | serviceExecutionId={} | issues={}", serviceExecutionId, issues);
    }
}
