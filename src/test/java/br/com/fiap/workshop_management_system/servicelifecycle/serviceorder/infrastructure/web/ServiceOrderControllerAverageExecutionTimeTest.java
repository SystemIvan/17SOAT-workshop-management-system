package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.web;

import br.com.fiap.workshop_management_system.identity.auth.application.port.TokenIssuer;
import br.com.fiap.workshop_management_system.testsupport.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class ServiceOrderControllerAverageExecutionTimeTest {

    private static final Instant FROM = Instant.parse("2126-08-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2126-09-01T00:00:00Z");

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TokenIssuer tokenIssuer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private String adminToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        adminToken = TestAuth.adminToken(tokenIssuer);
    }

    @Test
    void returnsGlobalAndCatalogServiceAveragesExclusivelyInHours() throws Exception {
        UUID serviceOrderId = insertServiceOrder();
        UUID catalogServiceId = UUID.randomUUID();
        insertExecution(serviceOrderId, catalogServiceId, FROM.plus(1, ChronoUnit.DAYS),
                FROM.plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS));
        insertExecution(serviceOrderId, catalogServiceId, FROM.plus(2, ChronoUnit.DAYS),
                FROM.plus(2, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS));

        mockMvc.perform(get("/api/service-orders/metrics/average-execution-time")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedFromInclusive").value(FROM.toString()))
                .andExpect(jsonPath("$.completedToExclusive").value(TO.toString()))
                .andExpect(jsonPath("$.unit").value("HOURS"))
                .andExpect(jsonPath("$.global.sampleCount").value(2))
                .andExpect(jsonPath("$.global.averageHours").value(1.5))
                .andExpect(jsonPath("$.global.averageSeconds").doesNotExist())
                .andExpect(jsonPath("$.global.averageMilliseconds").doesNotExist())
                .andExpect(jsonPath("$.byCatalogService.length()").value(1))
                .andExpect(jsonPath("$.byCatalogService[0].catalogServiceId")
                        .value(catalogServiceId.toString()))
                .andExpect(jsonPath("$.byCatalogService[0].sampleCount").value(2))
                .andExpect(jsonPath("$.byCatalogService[0].averageHours").value(1.5));
    }

    @Test
    void returnsNullAverageAndNoGroupsWhenThePeriodHasNoSamples() throws Exception {
        mockMvc.perform(get("/api/service-orders/metrics/average-execution-time")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unit").value("HOURS"))
                .andExpect(jsonPath("$.global.sampleCount").value(0))
                .andExpect(jsonPath("$.global.averageHours").value((Object) null))
                .andExpect(jsonPath("$.byCatalogService").isEmpty());
    }

    @Test
    void rejectsAnInvalidOrMissingPeriodWithStableCodes() throws Exception {
        mockMvc.perform(get("/api/service-orders/metrics/average-execution-time")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("from", TO.toString())
                        .param("to", FROM.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EXECUTION_TIME_PERIOD"));

        mockMvc.perform(get("/api/service-orders/metrics/average-execution-time")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("from", FROM.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private UUID insertServiceOrder() {
        UUID serviceOrderId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into service_orders (id, vehicle_year, status_snapshot, "
                        + "has_sent_estimate_with_pending_lines) values (?, ?, ?, ?)",
                serviceOrderId,
                2020,
                "COMPLETED",
                false);
        return serviceOrderId;
    }

    private void insertExecution(
            UUID serviceOrderId,
            UUID catalogServiceId,
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
                "COMPLETED",
                Timestamp.from(startedAt),
                Timestamp.from(completedAt));
    }
}
