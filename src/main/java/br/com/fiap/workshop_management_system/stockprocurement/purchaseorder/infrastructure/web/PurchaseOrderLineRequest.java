package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.infrastructure.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record PurchaseOrderLineRequest(
        @NotNull UUID stockItemId,
        @Positive int quantity) {
}
