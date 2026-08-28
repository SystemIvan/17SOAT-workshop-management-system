package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto
        .AverageServiceExecutionTimeResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto
        .CatalogServiceExecutionTimeAverageResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ExecutionTimeAverageResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.ExecutionTimeAggregate;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port
        .ServiceExecutionTimeMetricsReadPort;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ExecutionTimePeriod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Service
public class GetAverageServiceExecutionTimeUseCase {

    private static final int HOURS_SCALE = 2;
    private static final String HOURS = "HOURS";

    private final ServiceExecutionTimeMetricsReadPort metricsReadPort;

    public GetAverageServiceExecutionTimeUseCase(ServiceExecutionTimeMetricsReadPort metricsReadPort) {
        this.metricsReadPort = metricsReadPort;
    }

    @Transactional(readOnly = true)
    public AverageServiceExecutionTimeResponse execute(Instant fromInclusive, Instant toExclusive) {
        ExecutionTimePeriod period = new ExecutionTimePeriod(fromInclusive, toExclusive);
        ExecutionTimeAggregate global = metricsReadPort.findGlobal(period);
        var byCatalogService = metricsReadPort.findByCatalogService(period).stream()
                .map(aggregate -> new CatalogServiceExecutionTimeAverageResponse(
                        aggregate.catalogServiceId(),
                        aggregate.sampleCount(),
                        normalizeAverage(aggregate.sampleCount(), aggregate.averageHours())))
                .toList();
        return new AverageServiceExecutionTimeResponse(
                period.fromInclusive(),
                period.toExclusive(),
                HOURS,
                new ExecutionTimeAverageResponse(
                        global.sampleCount(), normalizeAverage(global.sampleCount(), global.averageHours())),
                byCatalogService);
    }

    private BigDecimal normalizeAverage(long sampleCount, BigDecimal averageHours) {
        if (sampleCount == 0) {
            return null;
        }
        if (averageHours == null) {
            throw new IllegalStateException("Average hours must be present when execution samples exist");
        }
        return averageHours.setScale(HOURS_SCALE, RoundingMode.HALF_UP);
    }
}
