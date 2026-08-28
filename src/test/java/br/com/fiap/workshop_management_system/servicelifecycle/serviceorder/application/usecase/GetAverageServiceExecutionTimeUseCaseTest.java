package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port
        .CatalogServiceExecutionTimeAggregate;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.ExecutionTimeAggregate;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port
        .ServiceExecutionTimeMetricsReadPort;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ExecutionTimePeriod;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model
        .InvalidExecutionTimePeriodException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetAverageServiceExecutionTimeUseCaseTest {

    private static final Instant FROM = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    void returnsGlobalAndGroupedAveragesInHoursForTheSamePeriod() {
        UUID catalogServiceId = UUID.randomUUID();
        StubMetricsReadPort port = new StubMetricsReadPort(
                new ExecutionTimeAggregate(3, new BigDecimal("1.555")),
                List.of(new CatalogServiceExecutionTimeAggregate(
                        catalogServiceId, 2, new BigDecimal("1.254"))));

        var response = new GetAverageServiceExecutionTimeUseCase(port).execute(FROM, TO);

        assertEquals(FROM, response.completedFromInclusive());
        assertEquals(TO, response.completedToExclusive());
        assertEquals("HOURS", response.unit());
        assertEquals(3, response.global().sampleCount());
        assertEquals(new BigDecimal("1.56"), response.global().averageHours());
        assertEquals(catalogServiceId, response.byCatalogService().getFirst().catalogServiceId());
        assertEquals(new BigDecimal("1.25"), response.byCatalogService().getFirst().averageHours());
        assertEquals(new ExecutionTimePeriod(FROM, TO), port.receivedPeriod);
    }

    @Test
    void distinguishesNoSamplesFromAnAverageRoundedToZero() {
        StubMetricsReadPort emptyPort = new StubMetricsReadPort(new ExecutionTimeAggregate(0, null), List.of());
        var emptyResponse = new GetAverageServiceExecutionTimeUseCase(emptyPort).execute(FROM, TO);

        assertEquals(0, emptyResponse.global().sampleCount());
        assertNull(emptyResponse.global().averageHours());

        StubMetricsReadPort zeroPort = new StubMetricsReadPort(
                new ExecutionTimeAggregate(1, new BigDecimal("0.001")), List.of());
        var zeroResponse = new GetAverageServiceExecutionTimeUseCase(zeroPort).execute(FROM, TO);

        assertEquals(1, zeroResponse.global().sampleCount());
        assertEquals(new BigDecimal("0.00"), zeroResponse.global().averageHours());
    }

    @Test
    void rejectsEmptyOrInvertedPeriodsWithoutQueryingPersistence() {
        StubMetricsReadPort port = new StubMetricsReadPort(new ExecutionTimeAggregate(0, null), List.of());
        GetAverageServiceExecutionTimeUseCase useCase = new GetAverageServiceExecutionTimeUseCase(port);

        assertThrows(InvalidExecutionTimePeriodException.class, () -> useCase.execute(FROM, FROM));
        assertThrows(InvalidExecutionTimePeriodException.class, () -> useCase.execute(TO, FROM));
        assertNull(port.receivedPeriod);
    }

    private static final class StubMetricsReadPort implements ServiceExecutionTimeMetricsReadPort {
        private final ExecutionTimeAggregate global;
        private final List<CatalogServiceExecutionTimeAggregate> byCatalogService;
        private ExecutionTimePeriod receivedPeriod;

        private StubMetricsReadPort(
                ExecutionTimeAggregate global,
                List<CatalogServiceExecutionTimeAggregate> byCatalogService) {
            this.global = global;
            this.byCatalogService = byCatalogService;
        }

        @Override
        public ExecutionTimeAggregate findGlobal(ExecutionTimePeriod period) {
            receivedPeriod = period;
            return global;
        }

        @Override
        public List<CatalogServiceExecutionTimeAggregate> findByCatalogService(ExecutionTimePeriod period) {
            assertEquals(receivedPeriod, period);
            return byCatalogService;
        }
    }
}
