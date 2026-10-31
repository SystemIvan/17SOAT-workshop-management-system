package br.com.fiap.workshop_management_system.stockprocurement.stock.application.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code type} carries the Stock Item type name ({@code PART}, {@code CONSUMABLE} or {@code SUPPLY}) so consumers
 * do not depend on this module's domain enum.
 */
public record StockItemQuote(
        UUID id,
        boolean active,
        String name,
        String type,
        BigDecimal price,
        String currency) {
}
