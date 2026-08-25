package br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.Estimate;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateLine;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateStockItem;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateStockAvailability;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EstimateResponse(
        UUID id,
        UUID serviceOrderId,
        UUID diagnosisId,
        UUID customerId,
        Instant createdAt,
        Instant expiresAt,
        String status,
        List<LineResponse> lines
) {

    public static EstimateResponse from(Estimate estimate) {
        return new EstimateResponse(
                estimate.id(),
                estimate.serviceOrderId(),
                estimate.diagnosisId(),
                estimate.customerId(),
                estimate.createdAt(),
                estimate.expiresAt(),
                estimate.status().name(),
                estimate.lines().stream()
                        .map(LineResponse::from)
                        .toList()
        );
    }

    public record LineResponse(
            UUID serviceExecutionId,
            String serviceName,
            MoneyResponse servicePrice,
            List<StockItemResponse> stockItems,
            List<StockAvailabilityResponse> stockAvailability
    ) {
        private static LineResponse from(EstimateLine line) {
            return new LineResponse(
                    line.serviceExecutionId(),
                    line.serviceName(),
                    new MoneyResponse(
                            line.servicePrice().value(),
                            line.servicePrice().currency()
                    ),
                    line.stockItems().stream()
                            .map(StockItemResponse::from)
                            .toList(),
                    line.stockAvailability().stream().map(StockAvailabilityResponse::from).toList()
            );
        }
    }

    public record StockAvailabilityResponse(
            UUID stockItemId,
            int requestedQuantity,
            int observedAvailableQuantity,
            int shortageQuantity,
            String status,
            Instant observedAt) {
        private static StockAvailabilityResponse from(EstimateStockAvailability availability) {
            return new StockAvailabilityResponse(availability.stockItemId(), availability.requestedQuantity(),
                    availability.observedAvailableQuantity(), availability.shortageQuantity(),
                    availability.status().name(), availability.observedAt());
        }
    }

    public record StockItemResponse(
            UUID stockItemId,
            String type,
            int quantity,
            String nameSnapshot,
            MoneyResponse priceSnapshot
    ) {
        private static StockItemResponse from(EstimateStockItem item) {
            return new StockItemResponse(
                    item.stockItemId(),
                    item.type().name(),
                    item.quantity(),
                    item.nameSnapshot(),
                    new MoneyResponse(
                            item.priceSnapshot().value(),
                            item.priceSnapshot().currency()
                    )
            );
        }
    }

    public record MoneyResponse(
            BigDecimal value,
            String currency
    ) {
    }
}
