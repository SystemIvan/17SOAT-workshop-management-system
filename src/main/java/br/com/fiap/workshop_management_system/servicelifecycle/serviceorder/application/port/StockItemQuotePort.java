package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockItemType;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface StockItemQuotePort {

    /**
     * Returns the quotes indexed by Stock Item ID; unknown IDs are absent from the map.
     */
    Map<UUID, StockItemQuote> quote(Collection<UUID> stockItemIds);

    record StockItemQuote(UUID id, boolean active, String name, StockItemType type, Money price) {
    }
}
