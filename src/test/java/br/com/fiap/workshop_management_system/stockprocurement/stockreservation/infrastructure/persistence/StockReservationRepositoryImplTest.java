package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.infrastructure.persistence;

import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.CurrencyCode;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Price;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Quantity;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Sku;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItemType;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.domain.model.StockReservation;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.domain.model.StockReservationLine;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.domain.model.StockReservationStatus;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.domain.repository.StockReservationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class StockReservationRepositoryImplTest {

    @Autowired
    private StockReservationRepository repository;

    @Autowired
    private StockItemRepository stockItemRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void persistsAndReconstitutesLinesStatusAndConsumptionTime() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        UUID serviceExecutionId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-08-20T12:00:00Z");
        Instant consumedAt = Instant.parse("2026-08-20T13:00:00Z");

        UUID reservationId = transactionTemplate.execute(status -> {
            StockItem stockItem = StockItem.create(
                    new Sku("RESERVATION-" + UUID.randomUUID()),
                    "Filter",
                    StockItemType.PART,
                    new Price(BigDecimal.ONE, CurrencyCode.BRL),
                    new Quantity(2));
            stockItemRepository.save(stockItem);
            StockReservation reservation = StockReservation.create(
                    serviceExecutionId,
                    List.of(new StockReservationLine(stockItem.id(), 2)),
                    createdAt);
            reservation.consume(consumedAt);
            repository.save(reservation);
            entityManager.flush();
            entityManager.clear();
            return reservation.id();
        });

        transactionTemplate.executeWithoutResult(status -> {
            StockReservation reservation = repository.findById(reservationId).orElseThrow();
            assertEquals(serviceExecutionId, reservation.serviceExecutionId());
            assertEquals(StockReservationStatus.CONSUMED, reservation.status());
            assertEquals(createdAt, reservation.createdAt());
            assertEquals(consumedAt, reservation.consumedAt());
            assertEquals(2, reservation.lines().getFirst().quantity());
        });
    }
}
