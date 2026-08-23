package br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.event.EstimateGenerated;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockItemType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EstimateTest {

    private EstimateStockItem newStockItem() {
        return new EstimateStockItem(
                UUID.randomUUID(),
                StockItemType.PART,
                2,
                "Filtro de oleo",
                Money.brl(new BigDecimal("35.90"))
        );
    }

    private EstimateLine newLine() {
        return new EstimateLine(
                UUID.randomUUID(),
                "Troca de oleo",
                Money.brl(new BigDecimal("120.00")),
                List.of(newStockItem())
        );
    }

    @Test
    void createdEstimateHasProvidedData() {
        UUID serviceOrderId = UUID.randomUUID();
        UUID diagnosisId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-08-16T15:00:00Z");
        Instant expiresAt = Instant.parse("2026-08-18T15:00:00Z");

        Estimate estimate = Estimate.create(
                serviceOrderId,
                diagnosisId,
                customerId,
                createdAt,
                expiresAt,
                List.of(newLine())
        );

        assertNotNull(estimate.id());
        assertEquals(serviceOrderId, estimate.serviceOrderId());
        assertEquals(diagnosisId, estimate.diagnosisId());
        assertEquals(customerId, estimate.customerId());
        assertEquals(createdAt, estimate.createdAt());
        assertEquals(expiresAt, estimate.expiresAt());
        assertEquals(1, estimate.lines().size());
        assertEquals(EstimateStatus.DRAFT, estimate.status());
    }

    @Test
    void markSentTransitionsFromDraftToSent() {
        Estimate estimate = Estimate.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now(), null, List.of(newLine()));

        estimate.markSent();

        assertEquals(EstimateStatus.SENT, estimate.status());
    }

    @Test
    void markSentFailsWhenNotDraft() {
        Estimate estimate = Estimate.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now(), null, List.of(newLine()));
        estimate.markSent();

        assertThrows(IllegalStateException.class, estimate::markSent);
    }

    @Test
    void closeTransitionsFromSentToClosed() {
        Estimate estimate = Estimate.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now(), null, List.of(newLine()));
        estimate.markSent();

        estimate.close();

        assertEquals(EstimateStatus.CLOSED, estimate.status());
    }

    @Test
    void closeFailsWhenNotSent() {
        Estimate draft = Estimate.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now(), null, List.of(newLine()));
        assertThrows(IllegalStateException.class, draft::close);

        Estimate closed = Estimate.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now(), null, List.of(newLine()));
        closed.markSent();
        closed.close();
        assertThrows(IllegalStateException.class, closed::close);
    }

    @Test
    void reconstitutePreservesPersistedStatus() {
        Estimate estimate = Estimate.reconstitute(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.now(),
                null,
                List.of(newLine()),
                EstimateStatus.CLOSED);

        assertEquals(EstimateStatus.CLOSED, estimate.status());
    }

    @Test
    void cannotCreateEstimateWithoutLines() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Estimate.create(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Instant.now(),
                        null,
                        List.of()
                )
        );
    }

    @Test
    void estimatePreservesCommercialSnapshots() {
        UUID stockItemId = UUID.randomUUID();
        UUID serviceExecutionId = UUID.randomUUID();

        EstimateStockItem stock = new EstimateStockItem(
                stockItemId,
                StockItemType.PART,
                3,
                "Pastilha de freio",
                Money.brl(new BigDecimal("89.90"))
        );

        EstimateLine line = new EstimateLine(
                serviceExecutionId,
                "Troca de pastilhas",
                Money.brl(new BigDecimal("150.00")),
                List.of(stock)
        );

        Estimate estimate = Estimate.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.now(),
                null,
                List.of(line)
        );

        EstimateLine savedLine = estimate.lines().getFirst();
        EstimateStockItem savedStock = savedLine.stockItems().getFirst();

        assertEquals(serviceExecutionId, savedLine.serviceExecutionId());
        assertEquals("Troca de pastilhas", savedLine.serviceName());
        assertEquals(Money.brl(new BigDecimal("150.00")), savedLine.servicePrice());
        assertEquals(stockItemId, savedStock.stockItemId());
        assertEquals(3, savedStock.quantity());
        assertEquals("Pastilha de freio", savedStock.nameSnapshot());
        assertEquals(Money.brl(new BigDecimal("89.90")), savedStock.priceSnapshot());
    }

    @Test
    void estimateCopiesLinesDefensively() {
        List<EstimateLine> mutableLines = new ArrayList<>();
        mutableLines.add(newLine());

        Estimate estimate = Estimate.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.now(),
                null,
                mutableLines
        );

        mutableLines.clear();

        assertEquals(1, estimate.lines().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> estimate.lines().add(newLine())
        );
    }

    @Test
    void estimateGeneratedExposesStableContract() {
        UUID estimateId = UUID.randomUUID();
        UUID serviceOrderId = UUID.randomUUID();
        UUID diagnosisId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-08-16T15:00:00Z");
        Instant expiresAt = Instant.parse("2026-08-18T15:00:00Z");

        EstimateGenerated event = EstimateGenerated.from(
                estimateId,
                serviceOrderId,
                diagnosisId,
                customerId,
                occurredAt,
                expiresAt
        );

        assertNotNull(event.eventId());
        assertEquals(occurredAt, event.occurredAt());
        assertEquals(estimateId, event.estimateId());
        assertEquals(serviceOrderId, event.serviceOrderId());
        assertEquals(diagnosisId, event.diagnosisId());
        assertEquals(customerId, event.customerId());
        assertEquals(expiresAt, event.expiresAt());
    }
}