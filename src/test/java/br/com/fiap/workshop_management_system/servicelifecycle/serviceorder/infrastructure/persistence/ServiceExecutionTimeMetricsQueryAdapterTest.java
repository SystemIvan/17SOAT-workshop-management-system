package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.persistence;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port
        .ServiceExecutionTimeMetricsReadPort;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ExecutionTimePeriod;
import jakarta.persistence.TemporalType;
import org.hibernate.dialect.DatabaseVersion;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.query.common.TemporalUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@Transactional
class ServiceExecutionTimeMetricsQueryAdapterTest {

    private static final Instant FROM = Instant.parse("2126-08-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2126-09-01T00:00:00Z");

    @Autowired
    private ServiceExecutionTimeMetricsReadPort metricsReadPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void calculatesGlobalAndCatalogServiceAveragesWithoutLoadingServiceOrders() {
        UUID serviceOrderId = insertServiceOrder();
        UUID firstCatalogServiceId = UUID.randomUUID();
        UUID secondCatalogServiceId = UUID.randomUUID();
        insertExecution(serviceOrderId, firstCatalogServiceId, "COMPLETED", FROM.plus(4, ChronoUnit.DAYS),
                FROM.plus(4, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS));
        insertExecution(serviceOrderId, firstCatalogServiceId, "COMPLETED", FROM.plus(5, ChronoUnit.DAYS),
                FROM.plus(5, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS));
        insertExecution(serviceOrderId, secondCatalogServiceId, "COMPLETED", FROM.plus(6, ChronoUnit.DAYS),
                FROM.plus(6, ChronoUnit.DAYS).plus(150, ChronoUnit.MINUTES));

        ExecutionTimePeriod period = new ExecutionTimePeriod(FROM, TO);
        var global = metricsReadPort.findGlobal(period);
        var groups = metricsReadPort.findByCatalogService(period);

        assertEquals(3, global.sampleCount());
        assertEquals(new BigDecimal("1.83333333"), global.averageHours());
        assertEquals(2, groups.size());
        var firstGroup = groups.stream()
                .filter(group -> group.catalogServiceId().equals(firstCatalogServiceId))
                .findFirst()
                .orElseThrow();
        var secondGroup = groups.stream()
                .filter(group -> group.catalogServiceId().equals(secondCatalogServiceId))
                .findFirst()
                .orElseThrow();
        assertEquals(2, firstGroup.sampleCount());
        assertEquals(new BigDecimal("1.50000000"), firstGroup.averageHours());
        assertEquals(1, secondGroup.sampleCount());
        assertEquals(new BigDecimal("2.50000000"), secondGroup.averageHours());
    }

    @Test
    void appliesCompletionBoundariesAndExcludesIneligibleRows() {
        UUID serviceOrderId = insertServiceOrder();
        UUID catalogServiceId = UUID.randomUUID();
        insertExecution(serviceOrderId, catalogServiceId, "COMPLETED", FROM, FROM);
        insertExecution(serviceOrderId, catalogServiceId, "COMPLETED", TO.minus(1, ChronoUnit.HOURS), TO);
        insertExecution(serviceOrderId, catalogServiceId, "IN_PROGRESS", FROM, FROM.plus(1, ChronoUnit.HOURS));
        insertExecution(serviceOrderId, catalogServiceId, "COMPLETED", null, null);

        var global = metricsReadPort.findGlobal(new ExecutionTimePeriod(FROM, TO));

        assertEquals(1, global.sampleCount());
        assertEquals(new BigDecimal("0E-8"), global.averageHours());
    }

    @Test
    void returnsNullAverageAndNoGroupsWhenThereAreNoSamples() {
        ExecutionTimePeriod period = new ExecutionTimePeriod(FROM, TO);

        var global = metricsReadPort.findGlobal(period);
        var groups = metricsReadPort.findByCatalogService(period);

        assertEquals(0, global.sampleCount());
        assertNull(global.averageHours());
        assertEquals(0, groups.size());
    }

    @Test
    void translatesNanosecondDurationToAMySqlSupportedMicrosecondExpression() {
        String pattern = new MySQLDialect(DatabaseVersion.make(8, 0)).timestampdiffPattern(
                TemporalUnit.NANOSECOND, TemporalType.TIMESTAMP, TemporalType.TIMESTAMP);

        assertEquals("timestampdiff(microsecond,?2,?3)*1e3", pattern);
    }

    private UUID insertServiceOrder() {
        UUID serviceOrderId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into service_orders (id, vehicle_year, status_snapshot, "
                        + "has_sent_estimate_with_pending_lines) values (?, ?, ?, ?)",
                serviceOrderId,
                2020,
                "IN_PROGRESS",
                false);
        return serviceOrderId;
    }

    private void insertExecution(
            UUID serviceOrderId,
            UUID catalogServiceId,
            String status,
            Instant startedAt,
            Instant completedAt) {
        jdbcTemplate.update(
                "insert into service_executions (id, service_order_id, catalog_service_id, name, price_value, "
                        + "price_currency, status, started_at, completed_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                serviceOrderId,
                catalogServiceId,
                "Oil change",
                BigDecimal.TEN,
                "BRL",
                status,
                startedAt == null ? null : Timestamp.from(startedAt),
                completedAt == null ? null : Timestamp.from(completedAt));
    }
}
