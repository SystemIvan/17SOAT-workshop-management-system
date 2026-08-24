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
import static br.com.fiap.workshop_management_system.testsupport.CatalogServiceHttpFixture
        .createActiveCatalogService;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RF19 - HTTP coverage for {@code POST /api/service-orders/{id}/executions/{executionId}/assign-technician}.
 * Authorization/rejection ({@code authorizeExecutionFromEstimate}/{@code rejectExecutionFromEstimate}) are
 * Epic 2 domain methods with no HTTP endpoint yet, so preconditions that need them are built by calling the
 * aggregate directly through {@link ServiceOrderRepository} instead of the (nonexistent) REST contract.
 */
@SpringBootTest
class ServiceOrderControllerAssignTechnicianTest {

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
    void assignsTechnicianToAPendingExecutionAndReturns200() throws Exception {
        String serviceOrderId = createServiceOrder();
        String executionId = diagnoseWithOneExecution(serviceOrderId);
        String technicianId = createTechnician();

        mockMvc.perform(post("/api/service-orders/{id}/executions/{executionId}/assign-technician",
                        serviceOrderId, executionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(technicianId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executions[0].assignedTechnicianId").value(technicianId));
    }

    @Test
    void returnsNotFoundWhenServiceOrderDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/service-orders/{id}/executions/{executionId}/assign-technician",
                        UUID.randomUUID(), UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(createTechnician())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void returnsNotFoundWhenServiceExecutionDoesNotExist() throws Exception {
        String serviceOrderId = createServiceOrder();

        mockMvc.perform(post("/api/service-orders/{id}/executions/{executionId}/assign-technician",
                        serviceOrderId, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(createTechnician())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void returnsNotFoundWhenTechnicianDoesNotExist() throws Exception {
        String serviceOrderId = createServiceOrder();
        String executionId = diagnoseWithOneExecution(serviceOrderId);

        mockMvc.perform(post("/api/service-orders/{id}/executions/{executionId}/assign-technician",
                        serviceOrderId, executionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(UUID.randomUUID().toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void returnsConflictWhenExecutionIsRejected() throws Exception {
        String serviceOrderId = createServiceOrder();
        String executionId = diagnoseWithOneExecution(serviceOrderId);
        rejectExecution(UUID.fromString(serviceOrderId), UUID.fromString(executionId));

        mockMvc.perform(post("/api/service-orders/{id}/executions/{executionId}/assign-technician",
                        serviceOrderId, executionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(createTechnician())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void returnsConflictWhenExecutionIsCompleted() throws Exception {
        String serviceOrderId = createServiceOrder();
        String executionId = diagnoseWithOneExecution(serviceOrderId);
        authorizeAndComplete(UUID.fromString(serviceOrderId), UUID.fromString(executionId));

        mockMvc.perform(post("/api/service-orders/{id}/executions/{executionId}/assign-technician",
                        serviceOrderId, executionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(createTechnician())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void returnsValidationErrorWhenTechnicianIdIsMissing() throws Exception {
        String serviceOrderId = createServiceOrder();
        String executionId = diagnoseWithOneExecution(serviceOrderId);

        mockMvc.perform(post("/api/service-orders/{id}/executions/{executionId}/assign-technician",
                        serviceOrderId, executionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void returnsValidationErrorWhenTechnicianIdIsNotAUuid() throws Exception {
        String serviceOrderId = createServiceOrder();
        String executionId = diagnoseWithOneExecution(serviceOrderId);

        mockMvc.perform(post("/api/service-orders/{id}/executions/{executionId}/assign-technician",
                        serviceOrderId, executionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"technicianId\":\"not-a-uuid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private void rejectExecution(UUID serviceOrderId, UUID executionId) {
        transactionTemplate.executeWithoutResult(status -> {
            ServiceOrder serviceOrder = serviceOrderRepository.findById(serviceOrderId).orElseThrow();
            serviceOrder.rejectExecutionFromEstimate(UUID.randomUUID(), executionId);
            serviceOrderRepository.save(serviceOrder);
        });
    }

    private void authorizeAndComplete(UUID serviceOrderId, UUID executionId) throws Exception {
        transactionTemplate.executeWithoutResult(status -> {
            ServiceOrder serviceOrder = serviceOrderRepository.findById(serviceOrderId).orElseThrow();
            serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);
            serviceOrderRepository.save(serviceOrder);
        });

        mockMvc.perform(post("/api/service-orders/{id}/executions/{executionId}/start", serviceOrderId, executionId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/service-orders/{id}/executions/{executionId}/complete", serviceOrderId, executionId))
                .andExpect(status().isOk());
    }

    private String createServiceOrder() throws Exception {
        String body = """
                {
                  "customerId": "%s",
                  "vehicleId": "%s",
                  "vehicleSnapshot": {"licensePlate": "ABC1D23", "brand": "Fiat", "model": "Uno", "year": 2015},
                  "priority": "NORMAL", "initialAssessment": "Initial assessment"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
        MvcResult result = mockMvc.perform(post("/api/service-orders")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String diagnoseWithOneExecution(String serviceOrderId) throws Exception {
        String technicianId = createTechnician();
        assignDiagnosisAssignee(serviceOrderId, technicianId);
        String body = """
                {
                  "diagnosedByTechnicianId": "%s",
                  "items": [
                    {"catalogServiceId": "%s", "name": "Troca de óleo", "price": {"value": 100.00, "currency": "BRL"}}
                  ]
                }
                """.formatted(technicianId, createActiveCatalogService(mockMvc));
        MvcResult result = mockMvc.perform(post("/api/service-orders/{id}/diagnosis", serviceOrderId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.executions[0].id");
    }

    private String createTechnician() throws Exception {
        String body = "{\"name\":\"Carlos Silva\",\"specialties\":[\"MECHANICAL\"]}";
        MvcResult result = mockMvc.perform(post("/api/technicians")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void assignDiagnosisAssignee(String serviceOrderId, String technicianId) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/service-orders/{id}/diagnosis-assignee", serviceOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(technicianId)))
                .andExpect(status().isOk());
    }

    private static String assignBody(String technicianId) {
        return "{\"technicianId\":\"" + technicianId + "\"}";
    }
}
