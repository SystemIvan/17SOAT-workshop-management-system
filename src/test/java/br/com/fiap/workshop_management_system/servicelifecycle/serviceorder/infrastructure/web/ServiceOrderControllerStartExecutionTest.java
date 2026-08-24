package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.web;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RF20 - HTTP coverage for {@code POST /api/service-orders/{id}/executions/{executionId}/start}.
 * Authorization ({@code authorizeExecutionFromEstimate}) is an Epic 2 domain method with no HTTP
 * endpoint yet, so the {@code READY} precondition is built by calling the aggregate directly through
 * {@link ServiceOrderRepository} instead of the (nonexistent) REST contract.
 */
@SpringBootTest
class ServiceOrderControllerStartExecutionTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ServiceOrderRepository serviceOrderRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private MockMvc mockMvc;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void startsAReadyExecutionAndReturns200() throws Exception {
        String serviceOrderId = createServiceOrder();
        String executionId = diagnoseWithOneExecution(serviceOrderId);
        authorizeExecution(UUID.fromString(serviceOrderId), UUID.fromString(executionId));

        mockMvc.perform(post("/api/service-orders/{id}/executions/{executionId}/start",
                        serviceOrderId, executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executions[0].status").value("IN_PROGRESS"));
    }

    @Test
    void returnsNotFoundWhenServiceOrderDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/service-orders/{id}/executions/{executionId}/start",
                        UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void returnsNotFoundWhenServiceExecutionDoesNotExist() throws Exception {
        String serviceOrderId = createServiceOrder();

        mockMvc.perform(post("/api/service-orders/{id}/executions/{executionId}/start",
                        serviceOrderId, UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void returnsConflictWhenExecutionIsNotAuthorizedYet() throws Exception {
        String serviceOrderId = createServiceOrder();
        String executionId = diagnoseWithOneExecution(serviceOrderId);

        mockMvc.perform(post("/api/service-orders/{id}/executions/{executionId}/start",
                        serviceOrderId, executionId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void returnsConflictWhenExecutionIsAlreadyInProgress() throws Exception {
        String serviceOrderId = createServiceOrder();
        String executionId = diagnoseWithOneExecution(serviceOrderId);
        authorizeExecution(UUID.fromString(serviceOrderId), UUID.fromString(executionId));

        mockMvc.perform(post("/api/service-orders/{id}/executions/{executionId}/start",
                        serviceOrderId, executionId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/service-orders/{id}/executions/{executionId}/start",
                        serviceOrderId, executionId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    private void authorizeExecution(UUID serviceOrderId, UUID executionId) {
        transactionTemplate.executeWithoutResult(status -> {
            ServiceOrder serviceOrder = serviceOrderRepository.findById(serviceOrderId).orElseThrow();
            serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);
            serviceOrderRepository.save(serviceOrder);
        });
    }

    private String createServiceOrder() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        ServiceOrderHttpTestFixture.persistActiveVehicle(context, customerId, vehicleId);
        String body = """
                {
                  "customerId": "%s",
                  "vehicleId": "%s",
                  "vehicleSnapshot": {"licensePlate": "ABC1D23", "brand": "Fiat", "model": "Uno", "year": 2015},
                  "priority": "NORMAL"
                }
                """.formatted(customerId, vehicleId);
        MvcResult result = mockMvc.perform(post("/api/service-orders")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String diagnoseWithOneExecution(String serviceOrderId) throws Exception {
        String body = """
                {
                  "items": [
                    {"catalogServiceId": "%s", "name": "Troca de óleo", "price": {"value": 100.00, "currency": "BRL"}}
                  ]
                }
                """.formatted(UUID.randomUUID());
        MvcResult result = mockMvc.perform(post("/api/service-orders/{id}/diagnosis", serviceOrderId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.executions[0].id");
    }
}
