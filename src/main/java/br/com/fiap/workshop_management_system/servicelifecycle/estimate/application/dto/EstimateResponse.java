package br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.Estimate;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateLine;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateStockAvailability;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateStockItem;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;

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
        MoneyResponse total,
        List<LineResponse> lines
) {

    public static EstimateResponse from(Estimate estimate) {
        Money total = calculateTotal(estimate);

        return new EstimateResponse(
                estimate.id(),
                estimate.serviceOrderId(),
                estimate.diagnosisId(),
                estimate.customerId(),
                estimate.createdAt(),
                estimate.expiresAt(),
                estimate.status().name(),
                MoneyResponse.from(total),
                estimate.lines().stream()
                        .map(LineResponse::from)
                        .toList()
        );
    }

    private static Money calculateTotal(Estimate estimate) {
        if (estimate.lines().isEmpty()) {
            return Money.brl(BigDecimal.ZERO);
        }

        String currency = estimate.lines()
                .getFirst()
                .lineTotal()
                .currency();

        BigDecimal value = estimate.lines().stream()
                .map(EstimateLine::lineTotal)
                .map(Money::value)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                );

        return new Money(value, currency);
    }

    public record LineResponse(
            UUID serviceExecutionId,
            String serviceName,
            MoneyResponse servicePrice,
            MoneyResponse lineTotal,
            List<StockItemResponse> stockItems,
            List<StockAvailabilityResponse> stockAvailability
    ) {

        private static LineResponse from(EstimateLine line) {
            return new LineResponse(
                    line.serviceExecutionId(),
                    line.serviceName(),
                    MoneyResponse.from(line.servicePrice()),
                    MoneyResponse.from(line.lineTotal()),
                    line.stockItems().stream()
                            .map(StockItemResponse::from)
                            .toList(),
                    line.stockAvailability().stream()
                            .map(StockAvailabilityResponse::from)
                            .toList()
            );
        }
    }

    public record StockAvailabilityResponse(
            UUID stockItemId,
            int requestedQuantity,
            int observedAvailableQuantity,
            int shortageQuantity,
            String status,
            Instant observedAt
    ) {

        private static StockAvailabilityResponse from(
                EstimateStockAvailability availability) {

            return new StockAvailabilityResponse(
                    availability.stockItemId(),
                    availability.requestedQuantity(),
                    availability.observedAvailableQuantity(),
                    availability.shortageQuantity(),
                    availability.status().name(),
                    availability.observedAt()
            );
        }
    }

    public record StockItemResponse(
            UUID stockItemId,
            String type,
            int quantity,
            String nameSnapshot,
            MoneyResponse priceSnapshot
    ) {

        private static StockItemResponse from(
                EstimateStockItem item) {

            return new StockItemResponse(
                    item.stockItemId(),
                    item.type().name(),
                    item.quantity(),
                    item.nameSnapshot(),
                    MoneyResponse.from(item.priceSnapshot())
            );
        }
    }

    public record MoneyResponse(
            BigDecimal value,
            String currency
    ) {

        private static MoneyResponse from(Money money) {
            return new MoneyResponse(
                    money.value(),
                    money.currency()
            );
        }
    }
}