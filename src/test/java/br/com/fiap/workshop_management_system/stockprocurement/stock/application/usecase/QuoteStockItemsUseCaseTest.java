package br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.stock.application.api.StockItemQuote;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.CurrencyCode;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Price;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Quantity;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Sku;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItemType;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteStockItemsUseCaseTest {

    @Mock
    private StockItemRepository repository;

    private QuoteStockItemsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new QuoteStockItemsUseCase(repository);
    }

    @Test
    void quotesItemsWithRegisteredNameTypeAndPrice() {
        StockItem filter = item("FILTER", "Filtro de óleo", StockItemType.PART, "45.90");
        StockItem inactive = item("OIL", "Óleo 5W30", StockItemType.CONSUMABLE, "32.00");
        inactive.deactivate();
        List<UUID> sortedIds = sorted(filter.id(), inactive.id());
        when(repository.findAllByIdForUpdate(sortedIds)).thenReturn(List.of(filter, inactive));

        List<StockItemQuote> quotes = useCase.quote(List.of(inactive.id(), filter.id(), filter.id()));

        StockItemQuote filterQuote = quotes.stream().filter(quote -> quote.id().equals(filter.id())).findFirst()
                .orElseThrow();
        assertTrue(filterQuote.active());
        assertEquals("Filtro de óleo", filterQuote.name());
        assertEquals("PART", filterQuote.type());
        assertEquals(new BigDecimal("45.90"), filterQuote.price());
        assertEquals("BRL", filterQuote.currency());
        assertFalse(quotes.stream().filter(quote -> quote.id().equals(inactive.id())).findFirst()
                .orElseThrow().active());
    }

    @Test
    void omitsUnknownItems() {
        UUID unknown = UUID.randomUUID();
        when(repository.findAllByIdForUpdate(List.of(unknown))).thenReturn(List.of());

        assertTrue(useCase.quote(List.of(unknown)).isEmpty());
    }

    @Test
    void skipsRepositoryForEmptyRequest() {
        assertTrue(useCase.quote(List.of()).isEmpty());
        verify(repository, never()).findAllByIdForUpdate(any());
    }

    private static List<UUID> sorted(UUID... ids) {
        return Arrays.stream(ids).sorted().toList();
    }

    private static StockItem item(String sku, String name, StockItemType type, String price) {
        return StockItem.create(new Sku(sku), name, type, new Price(new BigDecimal(price), CurrencyCode.BRL),
                new Quantity(10));
    }
}
