package br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository;

import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItemType;

import java.util.Set;

public record StockItemSearchCriteria(String search, Set<StockItemType> types, Boolean available, boolean active) {
    public StockItemSearchCriteria {
        search = search == null ? null : search.trim();
        if (search != null && search.isEmpty()) {
            search = null;
        }
        types = types == null ? Set.of() : Set.copyOf(types);
    }
}
