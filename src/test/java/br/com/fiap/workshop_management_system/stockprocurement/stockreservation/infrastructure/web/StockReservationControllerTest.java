package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.infrastructure.web;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.DiagnosisItem;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockItemType;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockRequirement;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.VehicleSnapshot;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.CurrencyCode;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Price;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Quantity;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Sku;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class StockReservationControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private StockItemRepository stockItemRepository;

    @Autowired
    private ServiceOrderRepository serviceOrderRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void retriesQueriesAndConsumesAStockReservationWithoutAcceptingLines() throws Exception {
        StockItem stockItem = StockItem.create(
                new Sku("HTTP-RESERVATION-" + System.nanoTime()),
                "Oil filter",
                br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItemType.PART,
                new Price(new BigDecimal("30.00"), CurrencyCode.BRL),
                new Quantity(2));
        stockItemRepository.save(stockItem);
        ServiceOrder serviceOrder = awaitingItemsServiceOrder(stockItem.id());
        UUID executionId = serviceOrder.serviceExecutions().getFirst().id();
        serviceOrderRepository.save(serviceOrder);

        MvcResult retryResult = mockMvc.perform(post(
                        "/api/service-orders/{id}/executions/{executionId}/stock-reservation",
                        serviceOrder.id(),
                        executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceExecutionId").value(executionId.toString()))
                .andExpect(jsonPath("$.outcome").value("RESERVED"))
                .andExpect(jsonPath("$.issues").isEmpty())
                .andReturn();
        String reservationId = com.jayway.jsonpath.JsonPath.read(
                retryResult.getResponse().getContentAsString(), "$.reservationId");

        mockMvc.perform(get("/api/stock-reservations/{reservationId}", reservationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.serviceExecutionId").value(executionId.toString()))
                .andExpect(jsonPath("$.lines[0].stockItemId").value(stockItem.id().toString()))
                .andExpect(jsonPath("$.lines[0].quantity").value(2));
        mockMvc.perform(get("/api/stock-reservations/by-service-execution/{executionId}", executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reservationId));

        mockMvc.perform(post("/api/stock-reservations/{reservationId}/consume", reservationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONSUMED"))
                .andExpect(jsonPath("$.consumedAt").isNotEmpty());
        mockMvc.perform(post("/api/stock-reservations/{reservationId}/consume", reservationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONSUMED"));
    }

    @Test
    void translatesInvalidIdsMissingReservationsAndInvalidRetryStates() throws Exception {
        mockMvc.perform(get("/api/stock-reservations/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/stock-reservations/{reservationId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STOCK_RESERVATION_NOT_FOUND"));

        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015), "Initial assessment");
        serviceOrder.assignDiagnosisAssignee(UUID.randomUUID());
        serviceOrder.performDiagnosis(List.of(new DiagnosisItem(
                UUID.randomUUID(),
                "Oil change",
                Money.brl(BigDecimal.TEN),
                List.of())), UUID.randomUUID(), java.time.Instant.EPOCH);
        UUID executionId = serviceOrder.serviceExecutions().getFirst().id();
        serviceOrderRepository.save(serviceOrder);

        mockMvc.perform(post(
                        "/api/service-orders/{id}/executions/{executionId}/stock-reservation",
                        serviceOrder.id(),
                        executionId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void publishesStockReservationOperationsInGeneratedOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/stock-reservations/{reservationId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/stock-reservations/{reservationId}/consume'].post").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/service-orders/{id}/executions/{executionId}/stock-reservation'].post")
                        .exists());
    }

    private ServiceOrder awaitingItemsServiceOrder(UUID stockItemId) {
        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015), "Initial assessment");
        serviceOrder.assignDiagnosisAssignee(UUID.randomUUID());
        UUID diagnosisId = serviceOrder.performDiagnosis(List.of(new DiagnosisItem(
                UUID.randomUUID(),
                "Oil change",
                Money.brl(BigDecimal.TEN),
                List.of(new StockRequirement(
                        stockItemId,
                        StockItemType.PART,
                        2,
                        "Oil filter",
                        Money.brl(BigDecimal.TEN),
                        false)))), UUID.randomUUID(), java.time.Instant.EPOCH);
        UUID executionId = serviceOrder.serviceExecutions().getFirst().id();
        serviceOrder.freezeStockRequirements(diagnosisId);
        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);
        return serviceOrder;
    }
}
