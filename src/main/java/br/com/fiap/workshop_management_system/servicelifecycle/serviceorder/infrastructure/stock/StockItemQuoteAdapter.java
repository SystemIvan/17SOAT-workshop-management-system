package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.stock;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.StockItemQuotePort;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockItemType;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.api.StockItemQuoteApi;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class StockItemQuoteAdapter implements StockItemQuotePort {

    private final StockItemQuoteApi stockItemQuoteApi;

    public StockItemQuoteAdapter(StockItemQuoteApi stockItemQuoteApi) {
        this.stockItemQuoteApi = stockItemQuoteApi;
    }

    @Override
    public Map<UUID, StockItemQuote> quote(Collection<UUID> stockItemIds) {
        return stockItemQuoteApi.quote(stockItemIds).stream()
                .map(quote -> new StockItemQuote(quote.id(), quote.active(), quote.name(), toType(quote.type()),
                        new Money(quote.price(), quote.currency())))
                .collect(Collectors.toMap(StockItemQuote::id, Function.identity()));
    }

    // An unknown type is contract drift between modules: it must surface as a technical failure (500), not as the
    // 400 that a raw IllegalArgumentException would receive from GlobalExceptionHandler.
    private static StockItemType toType(String type) {
        try {
            return StockItemType.valueOf(type);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new StockItemQuoteContractException("Unsupported stock item type from stockprocurement");
        }
    }

    static class StockItemQuoteContractException extends RuntimeException {

        StockItemQuoteContractException(String message) {
            super(message);
        }
    }
}
