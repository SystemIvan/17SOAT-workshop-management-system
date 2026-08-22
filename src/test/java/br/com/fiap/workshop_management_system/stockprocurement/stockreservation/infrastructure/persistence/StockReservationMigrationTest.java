package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockReservationMigrationTest {

    @Test
    void upgradesLegacyStatusesRequirementsAndSnapshotsWithoutInventingReservations() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:stock_reservation_migration_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("20260817190136")
                .load()
                .migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        UUID serviceOrderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID diagnosisId = UUID.randomUUID();
        UUID readyExecutionId = UUID.randomUUID();
        UUID inProgressExecutionId = UUID.randomUUID();
        insertServiceOrder(jdbcTemplate, serviceOrderId, customerId, diagnosisId);
        insertExecution(jdbcTemplate, readyExecutionId, serviceOrderId, diagnosisId, "READY");
        insertExecution(jdbcTemplate, inProgressExecutionId, serviceOrderId, diagnosisId, "IN_PROGRESS");
        insertRequirement(jdbcTemplate, readyExecutionId, true);
        insertRequirement(jdbcTemplate, inProgressExecutionId, true);
        insertEstimateWithLines(
                jdbcTemplate,
                serviceOrderId,
                customerId,
                diagnosisId,
                readyExecutionId,
                inProgressExecutionId);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertEquals("AWAITING_ITEMS", statusOf(jdbcTemplate, readyExecutionId));
        assertEquals("IN_PROGRESS", statusOf(jdbcTemplate, inProgressExecutionId));
        assertTrue(Boolean.TRUE.equals(frozenOf(jdbcTemplate, readyExecutionId)));
        assertTrue(Boolean.TRUE.equals(frozenOf(jdbcTemplate, inProgressExecutionId)));
        assertFalse(Boolean.TRUE.equals(reservedOf(jdbcTemplate, readyExecutionId)));
        assertNull(reservationIdOf(jdbcTemplate, readyExecutionId));
        assertNull(reservationIdOf(jdbcTemplate, inProgressExecutionId));
        assertEquals("AWAITING_ITEMS", orderStatusOf(jdbcTemplate, serviceOrderId));
        assertEquals(0, jdbcTemplate.queryForObject("select count(*) from stock_reservations", Integer.class));
    }

    private void insertServiceOrder(
            JdbcTemplate jdbcTemplate,
            UUID serviceOrderId,
            UUID customerId,
            UUID diagnosisId) {
        jdbcTemplate.update(
                "insert into service_orders (id, customer_id, vehicle_id, vehicle_license_plate, vehicle_brand, "
                        + "vehicle_model, vehicle_year, priority, status_snapshot, open_diagnosis_id, "
                        + "has_sent_estimate_with_pending_lines) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                serviceOrderId,
                customerId,
                UUID.randomUUID(),
                "ABC1D23",
                "Fiat",
                "Uno",
                2015,
                "NORMAL",
                "AWAITING_APPROVAL",
                diagnosisId,
                true);
    }

    private void insertExecution(
            JdbcTemplate jdbcTemplate,
            UUID executionId,
            UUID serviceOrderId,
            UUID diagnosisId,
            String status) {
        jdbcTemplate.update(
                "insert into service_executions (id, service_order_id, diagnosis_id, catalog_service_id, name, "
                        + "price_value, price_currency, status) values (?, ?, ?, ?, ?, ?, ?, ?)",
                executionId,
                serviceOrderId,
                diagnosisId,
                UUID.randomUUID(),
                "Oil change",
                BigDecimal.TEN,
                "BRL",
                status);
    }

    private void insertRequirement(JdbcTemplate jdbcTemplate, UUID executionId, boolean reserved) {
        jdbcTemplate.update(
                "insert into service_execution_stock_requirements (service_execution_id, stock_item_id, type, "
                        + "quantity, name_snapshot, price_snapshot_value, price_snapshot_currency, reserved) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?)",
                executionId,
                UUID.randomUUID(),
                "PART",
                1,
                "Filter",
                BigDecimal.ONE,
                "BRL",
                reserved);
    }

    private void insertEstimateWithLines(
            JdbcTemplate jdbcTemplate,
            UUID serviceOrderId,
            UUID customerId,
            UUID diagnosisId,
            UUID readyExecutionId,
            UUID inProgressExecutionId) {
        UUID estimateId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into estimates (id, service_order_id, diagnosis_id, customer_id, created_at) "
                        + "values (?, ?, ?, ?, ?)",
                estimateId,
                serviceOrderId,
                diagnosisId,
                customerId,
                Timestamp.from(Instant.parse("2026-08-20T12:00:00Z")));
        insertEstimateLine(jdbcTemplate, estimateId, 0, readyExecutionId);
        insertEstimateLine(jdbcTemplate, estimateId, 1, inProgressExecutionId);
    }

    private void insertEstimateLine(JdbcTemplate jdbcTemplate, UUID estimateId, int lineOrder, UUID executionId) {
        jdbcTemplate.update(
                "insert into estimate_lines (id, estimate_id, line_order, service_execution_id, service_name, "
                        + "service_price_value, service_price_currency) values (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                estimateId,
                lineOrder,
                executionId,
                "Oil change",
                BigDecimal.TEN,
                "BRL");
    }

    private String statusOf(JdbcTemplate jdbcTemplate, UUID executionId) {
        return jdbcTemplate.queryForObject(
                "select status from service_executions where id = ?", String.class, executionId);
    }

    private Boolean frozenOf(JdbcTemplate jdbcTemplate, UUID executionId) {
        return jdbcTemplate.queryForObject(
                "select stock_requirements_frozen from service_executions where id = ?", Boolean.class, executionId);
    }

    private Boolean reservedOf(JdbcTemplate jdbcTemplate, UUID executionId) {
        return jdbcTemplate.queryForObject(
                "select reserved from service_execution_stock_requirements where service_execution_id = ?",
                Boolean.class,
                executionId);
    }

    private UUID reservationIdOf(JdbcTemplate jdbcTemplate, UUID executionId) {
        return jdbcTemplate.queryForObject(
                "select stock_reservation_id from service_executions where id = ?", UUID.class, executionId);
    }

    private String orderStatusOf(JdbcTemplate jdbcTemplate, UUID serviceOrderId) {
        return jdbcTemplate.queryForObject(
                "select status_snapshot from service_orders where id = ?", String.class, serviceOrderId);
    }
}
