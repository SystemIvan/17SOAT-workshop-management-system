package br.com.fiap.workshop_management_system.servicelifecycle.estimate.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that V20260823120000__add_status_to_estimates.sql backfills SENT for rows that existed
 * before the status column was introduced (AD-008, estimate-lifecycle-status).
 */
class EstimateStatusMigrationTest {

    @Test
    void backfillsSentForEstimatesPersistedBeforeTheStatusColumnExisted() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:estimate_status_migration_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("20260822180858")
                .load()
                .migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        UUID serviceOrderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID diagnosisId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        UUID estimateId = UUID.randomUUID();

        jdbcTemplate.update(
                "insert into service_orders (id, customer_id, vehicle_id, vehicle_license_plate, vehicle_brand, "
                        + "vehicle_model, vehicle_year, priority, status_snapshot, open_diagnosis_id, "
                        + "has_sent_estimate_with_pending_lines) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                serviceOrderId, customerId, UUID.randomUUID(), "ABC1D23", "Fiat", "Uno", 2015,
                "NORMAL", "AWAITING_APPROVAL", diagnosisId, true);
        jdbcTemplate.update(
                "insert into service_executions (id, service_order_id, diagnosis_id, catalog_service_id, name, "
                        + "price_value, price_currency, status) values (?, ?, ?, ?, ?, ?, ?, ?)",
                executionId, serviceOrderId, diagnosisId, UUID.randomUUID(), "Oil change",
                BigDecimal.TEN, "BRL", "PENDING");
        jdbcTemplate.update(
                "insert into estimates (id, service_order_id, diagnosis_id, customer_id, created_at) "
                        + "values (?, ?, ?, ?, ?)",
                estimateId, serviceOrderId, diagnosisId, customerId,
                Timestamp.from(Instant.parse("2026-08-20T12:00:00Z")));
        jdbcTemplate.update(
                "insert into estimate_lines (id, estimate_id, line_order, service_execution_id, service_name, "
                        + "service_price_value, service_price_currency) values (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), estimateId, 0, executionId, "Oil change", BigDecimal.TEN, "BRL");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        String status = jdbcTemplate.queryForObject(
                "select status from estimates where id = ?", String.class, estimateId);
        assertEquals("SENT", status);
    }
}
