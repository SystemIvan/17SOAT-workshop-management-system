package br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.Estimate;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateLine;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateStockItem;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockItemType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EstimateResponseTest {

    @Test
    void calculatesEstimateTotalFromAllLineTotals() {
        EstimateLine firstLine = new EstimateLine(
                UUID.randomUUID(),
                "Troca de oleo",
                Money.brl(new BigDecimal("120.00")),
                List.of(
                        new EstimateStockItem(
                                UUID.randomUUID(),
                                StockItemType.PART,
                                2,
                                "Filtro de oleo",
                                Money.brl(new BigDecimal("35.90"))
                        )
                )
        );

        EstimateLine secondLine = new EstimateLine(
                UUID.randomUUID(),
                "Alinhamento",
                Money.brl(new BigDecimal("90.00")),
                List.of()
        );

        Instant createdAt =
                Instant.parse("2026-08-26T15:00:00Z");

        Estimate estimate = Estimate.reconstitute(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                createdAt,
                createdAt.plusSeconds(24 * 60 * 60),
                List.of(firstLine, secondLine),
                EstimateStatus.SENT
        );

        EstimateResponse response =
                EstimateResponse.from(estimate);

        assertEquals(
                new BigDecimal("281.80"),
                response.total().value()
        );

        assertEquals(
                "BRL",
                response.total().currency()
        );

        assertEquals(
                new BigDecimal("191.80"),
                response.lines().get(0).lineTotal().value()
        );

        assertEquals(
                new BigDecimal("90.00"),
                response.lines().get(1).lineTotal().value()
        );
    }
}