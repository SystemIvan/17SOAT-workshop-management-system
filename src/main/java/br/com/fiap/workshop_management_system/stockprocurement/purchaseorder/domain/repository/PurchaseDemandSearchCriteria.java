package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.repository;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseDemandOrigin;

import java.util.UUID;

public record PurchaseDemandSearchCriteria(PurchaseDemandOrigin origin, UUID stockItemId) {
}
