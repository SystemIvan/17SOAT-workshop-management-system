package br.com.fiap.workshop_management_system.stockprocurement.stock.application.api;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Returns the current name, type and price of Stock Items inside the caller's transaction. Unknown IDs are simply
 * absent from the result.
 */
public interface StockItemQuoteApi {

    List<StockItemQuote> quote(Collection<UUID> stockItemIds);
}
