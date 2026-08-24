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
import br.com.fiap.workshop_management_system.identity.auth.application.port.TokenIssuer;
import br.com.fiap.workshop_management_system.testsupport.TestAuth;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RF21 - HTTP coverage for {@code PATCH /api/service-orders/{id}/executions/{executionId}/progress}.
 * Authorization ({@code authorizeExecutionFromEstimate}) and start ({@code startExecution}) precondition
 * state is built by calling the aggregate directly through {@link ServiceOrderRepository}, same pattern
 * as {@code ServiceOrderControllerStartExecutionTest}, since RF19's Epic 2 authorize step has no HTTP
 * endpoint yet.
 */
@SpringBootTest
class ServiceOrderControllerUpdateProgressTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TokenIssuer tokenIssuer;

    @Autowired
    private ServiceOrderRepository serviceOrderRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private MockMvc mockMvc;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                        .springSecurity())
                .defaultRequest(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/").header("Authorization", "Bearer " + TestAuth.adminToken(tokenIssuer)))
                .build();
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void updatesProgressOfAnInProgressExecutionAndReturns200() throws Exception {
        String serviceOrderId = createServiceOrder();
        String executionId = diagnoseWithOneExecution(serviceOrderId);
        authorizeAndStartExecution(UUID.fromString(serviceOrderId), UUID.fromString(executionId));

        mockMvc.perform(patch("/api/service-orders/{id}/executions/{executionId}/progress",
                        serviceOrderId, executionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"note": "Peça trocada, aguardando teste"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executions[0].status").value("IN_PROGRESS"));
    }

    @Test
    void returnsNotFoundWhenServiceOrderDoesNotExist() throws Exception {
        mockMvc.perform(patch("/api/service-orders/{id}/executions/{executionId}/progress",
                        UUID.randomUUID(), UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"note": "nota"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void returnsNotFoundWhenServiceExecutionDoesNotExist() throws Exception {
        String serviceOrderId = createServiceOrder();

        mockMvc.perform(patch("/api/service-orders/{id}/executions/{executionId}/progress",
                        serviceOrderId, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"note": "nota"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void returnsConflictWhenExecutionHasNotStartedYet() throws Exception {
        String serviceOrderId = createServiceOrder();
        String executionId = diagnoseWithOneExecution(serviceOrderId);

        mockMvc.perform(patch("/api/service-orders/{id}/executions/{executionId}/progress",
                        serviceOrderId, executionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"note": "nota"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void returnsBadRequestWhenNoteIsBlank() throws Exception {
        String serviceOrderId = createServiceOrder();
        String executionId = diagnoseWithOneExecution(serviceOrderId);
        authorizeAndStartExecution(UUID.fromString(serviceOrderId), UUID.fromString(executionId));

        mockMvc.perform(patch("/api/service-orders/{id}/executions/{executionId}/progress",
                        serviceOrderId, executionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"note": "  "}
                                """))
                .andExpect(status().isBadRequest());
    }

    private void authorizeAndStartExecution(UUID serviceOrderId, UUID executionId) {
        transactionTemplate.executeWithoutResult(status -> {
            ServiceOrder serviceOrder = serviceOrderRepository.findById(serviceOrderId).orElseThrow();
            serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);
            serviceOrder.startExecution(executionId);
            serviceOrderRepository.save(serviceOrder);
        });
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
        String technicianId = assignDiagnosisAssignee(serviceOrderId);
        String body = """
                {
                  "diagnosedByTechnicianId": "%s",
                  "items": [
                    {"catalogServiceId": "%s", "name": "Troca de óleo", "price": {"value": 100.00, "currency": "BRL"}}
                  ]
                }
                """.formatted(technicianId, UUID.randomUUID());
        MvcResult result = mockMvc.perform(post("/api/service-orders/{id}/diagnosis", serviceOrderId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.executions[0].id");
    }

    private String assignDiagnosisAssignee(String serviceOrderId) throws Exception {
        MvcResult technician = mockMvc.perform(post("/api/technicians").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Carlos Silva\",\"specialties\":[\"MECHANICAL\"]}"))
                .andExpect(status().isCreated()).andReturn();
        String technicianId = JsonPath.read(technician.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/service-orders/{id}/diagnosis-assignee", serviceOrderId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"technicianId\":\"" + technicianId + "\"}"))
                .andExpect(status().isOk());
        return technicianId;
    }
}
