package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.stock;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.StockItemQuotePort;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockItemType;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.api.StockItemQuote;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.api.StockItemQuoteApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StockItemQuoteAdapterTest {

    @ParameterizedTest
    @EnumSource(StockItemType.class)
    void mapsQuotesByIdWithConsumerTypeAndMoney(StockItemType type) {
        StockItemQuoteApi api = mock(StockItemQuoteApi.class);
        UUID id = UUID.randomUUID();
        List<UUID> ids = List.of(id);
        when(api.quote(ids)).thenReturn(List.of(
                new StockItemQuote(id, true, "Filtro", type.name(), new BigDecimal("45.90"), "BRL")));

        Map<UUID, StockItemQuotePort.StockItemQuote> quotes = new StockItemQuoteAdapter(api).quote(ids);

        StockItemQuotePort.StockItemQuote quote = quotes.get(id);
        assertTrue(quote.active());
        assertEquals("Filtro", quote.name());
        assertEquals(type, quote.type());
        assertEquals(new Money(new BigDecimal("45.90"), "BRL"), quote.price());
    }

    @Test
    void returnsEmptyMapWhenNothingIsFound() {
        StockItemQuoteApi api = mock(StockItemQuoteApi.class);
        List<UUID> ids = List.of(UUID.randomUUID());
        when(api.quote(ids)).thenReturn(List.of());

        assertTrue(new StockItemQuoteAdapter(api).quote(ids).isEmpty());
    }

    @Test
    void failsLoudlyOnUnknownTypeContractDrift() {
        StockItemQuoteApi api = mock(StockItemQuoteApi.class);
        UUID id = UUID.randomUUID();
        List<UUID> ids = List.of(id);
        when(api.quote(ids)).thenReturn(List.of(
                new StockItemQuote(id, true, "Item", "TOOL", BigDecimal.ONE, "BRL")));

        assertThrows(StockItemQuoteAdapter.StockItemQuoteContractException.class,
                () -> new StockItemQuoteAdapter(api).quote(ids));
    }
}
