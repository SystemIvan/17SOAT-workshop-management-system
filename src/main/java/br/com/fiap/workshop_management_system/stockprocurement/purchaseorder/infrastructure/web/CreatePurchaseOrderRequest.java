package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.infrastructure.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreatePurchaseOrderRequest(
        @Size(max = 100) List<@NotNull UUID> demandIds,
        @NotEmpty @Size(max = 100) List<@NotNull @Valid PurchaseOrderLineRequest> lines) {
}
