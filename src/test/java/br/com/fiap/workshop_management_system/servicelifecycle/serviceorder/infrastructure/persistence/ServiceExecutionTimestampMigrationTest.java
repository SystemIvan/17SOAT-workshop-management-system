package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceExecutionTimestampMigrationTest {

    @Test
    void addsNullableTimestampsConstraintAndMetricsIndexWithoutBackfill() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:execution_timestamp_migration_" + UUID.randomUUID()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("20260826180212")
                .load()
                .migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        UUID serviceOrderId = insertServiceOrder(jdbcTemplate);
        UUID legacyExecutionId = insertLegacyExecution(jdbcTemplate, serviceOrderId);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertNull(jdbcTemplate.queryForObject(
                "select started_at from service_executions where id = ?", Timestamp.class, legacyExecutionId));
        assertNull(jdbcTemplate.queryForObject(
                "select completed_at from service_executions where id = ?", Timestamp.class, legacyExecutionId));

        Instant startedAt = Instant.parse("2026-08-28T15:00:00Z");
        assertThrows(DataIntegrityViolationException.class, () -> insertExecution(
                jdbcTemplate,
                serviceOrderId,
                startedAt,
                startedAt.minusSeconds(1)));
        assertTrue(hasIndex(dataSource, "IDX_SERVICE_EXECUTIONS_EXECUTION_TIME_METRICS"));
    }

    private UUID insertServiceOrder(JdbcTemplate jdbcTemplate) {
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

    private UUID insertExecution(
            JdbcTemplate jdbcTemplate,
            UUID serviceOrderId,
            Instant startedAt,
            Instant completedAt) {
        UUID executionId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into service_executions (id, service_order_id, catalog_service_id, name, price_value, "
                        + "price_currency, status, started_at, completed_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                executionId,
                serviceOrderId,
                UUID.randomUUID(),
                "Oil change",
                BigDecimal.TEN,
                "BRL",
                completedAt == null ? "IN_PROGRESS" : "COMPLETED",
                startedAt == null ? null : Timestamp.from(startedAt),
                completedAt == null ? null : Timestamp.from(completedAt));
        return executionId;
    }

    private UUID insertLegacyExecution(JdbcTemplate jdbcTemplate, UUID serviceOrderId) {
        UUID executionId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into service_executions (id, service_order_id, catalog_service_id, name, price_value, "
                        + "price_currency, status) values (?, ?, ?, ?, ?, ?, ?)",
                executionId,
                serviceOrderId,
                UUID.randomUUID(),
                "Oil change",
                BigDecimal.TEN,
                "BRL",
                "IN_PROGRESS");
        return executionId;
    }

    private boolean hasIndex(DriverManagerDataSource dataSource, String indexName) throws Exception {
        try (var connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet indexes = metadata.getIndexInfo(null, null, "SERVICE_EXECUTIONS", false, false)) {
                while (indexes.next()) {
                    if (indexName.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
