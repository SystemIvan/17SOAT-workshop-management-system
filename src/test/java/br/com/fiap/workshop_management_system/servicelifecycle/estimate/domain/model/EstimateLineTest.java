package br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockItemType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EstimateLineTest {

    @Test
    void calculatesLineTotalIncludingServiceAndStockItems() {
        EstimateStockItem stockItem = new EstimateStockItem(
                UUID.randomUUID(),
                StockItemType.PART,
                2,
                "Filtro de oleo",
                Money.brl(new BigDecimal("35.90"))
        );

        EstimateLine line = new EstimateLine(
                UUID.randomUUID(),
                "Troca de oleo",
                Money.brl(new BigDecimal("120.00")),
                List.of(stockItem)
        );

        Money total = line.lineTotal();

        assertEquals(
                new BigDecimal("191.80"),
                total.value()
        );

        assertEquals(
                "BRL",
                total.currency()
        );
    }

    @Test
    void lineTotalEqualsServicePriceWhenThereAreNoStockItems() {
        EstimateLine line = new EstimateLine(
                UUID.randomUUID(),
                "Alinhamento",
                Money.brl(new BigDecimal("90.00")),
                List.of()
        );

        Money total = line.lineTotal();

        assertEquals(
                new BigDecimal("90.00"),
                total.value()
        );

        assertEquals(
                "BRL",
                total.currency()
        );
    }
}