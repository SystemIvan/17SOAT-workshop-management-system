package br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record EstimateLine(
        UUID serviceExecutionId,
        String serviceName,
        Money servicePrice,
        List<EstimateStockItem> stockItems,
        List<EstimateStockAvailability> stockAvailability
) {

    public EstimateLine(
            UUID serviceExecutionId,
            String serviceName,
            Money servicePrice,
            List<EstimateStockItem> stockItems) {

        this(
                serviceExecutionId,
                serviceName,
                servicePrice,
                stockItems,
                List.of()
        );
    }

    public EstimateLine {
        Objects.requireNonNull(
                serviceExecutionId,
                "serviceExecutionId must not be null"
        );

        Objects.requireNonNull(
                serviceName,
                "serviceName must not be null"
        );

        Objects.requireNonNull(
                servicePrice,
                "servicePrice must not be null"
        );

        if (serviceName.isBlank()) {
            throw new IllegalArgumentException(
                    "serviceName must not be blank"
            );
        }

        stockItems = stockItems == null
                ? List.of()
                : List.copyOf(stockItems);

        stockAvailability = stockAvailability == null
                ? List.of()
                : List.copyOf(stockAvailability);
    }

    public Money lineTotal() {
        BigDecimal stockItemsTotal = stockItems.stream()
                .map(item ->
                        item.priceSnapshot()
                                .value()
                                .multiply(
                                        BigDecimal.valueOf(
                                                item.quantity()
                                        )
                                )
                )
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                );

        return new Money(
                servicePrice.value().add(stockItemsTotal),
                servicePrice.currency()
        );
    }
}