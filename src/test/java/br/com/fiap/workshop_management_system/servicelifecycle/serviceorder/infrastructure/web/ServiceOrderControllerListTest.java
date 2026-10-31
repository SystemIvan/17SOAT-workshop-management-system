package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.web;

import br.com.fiap.workshop_management_system.identity.auth.application.port.TokenIssuer;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockItemType;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockRequirement;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.persistence
        .ServiceOrderJpaRepository;
import br.com.fiap.workshop_management_system.testsupport.TestAuth;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static br.com.fiap.workshop_management_system.testsupport.CatalogServiceHttpFixture.createActiveCatalogService;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RF34 - HTTP coverage for {@code GET /api/service-orders}: no filter, each individual filter,
 * AND-combination of filters and validation errors. {@code GET /{id}} coverage lives in
 * {@code GetServiceOrderUseCaseTest}/`track-execution`; this class only guards against regression.
 */
@SpringBootTest
class ServiceOrderControllerListTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TokenIssuer tokenIssuer;

    @Autowired
    private ServiceOrderJpaRepository serviceOrderJpaRepository;

    @Autowired
    private ServiceOrderRepository serviceOrderRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    private MockMvc mockMvc;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        serviceOrderJpaRepository.deleteAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                        .springSecurity())
                .defaultRequest(get("/").header("Authorization", "Bearer " + TestAuth.adminToken(tokenIssuer)))
                .build();
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void returnsEmptyArrayWhenThereAreNoServiceOrders() throws Exception {
        mockMvc.perform(get("/api/service-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void returnsEveryServiceOrderWhenNoFilterIsProvided() throws Exception {
        String first = createServiceOrder(UUID.randomUUID(), "NORMAL");
        String second = createServiceOrder(UUID.randomUUID(), "HIGH");

        mockMvc.perform(get("/api/service-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(first, second)));
    }

    @Test
    void filtersByStatus() throws Exception {
        String received = createServiceOrder(UUID.randomUUID(), "NORMAL");
        String inDiagnosis = createServiceOrder(UUID.randomUUID(), "NORMAL");
        diagnoseWithOneExecution(inDiagnosis);

        mockMvc.perform(get("/api/service-orders").param("status", "IN_DIAGNOSIS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(inDiagnosis));

        mockMvc.perform(get("/api/service-orders").param("status", "RECEIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(received));
    }

    @Test
    void filtersByCustomerId() throws Exception {
        UUID targetCustomerId = UUID.randomUUID();
        String target = createServiceOrder(targetCustomerId, "NORMAL");
        createServiceOrder(UUID.randomUUID(), "NORMAL");

        mockMvc.perform(get("/api/service-orders").param("customerId", targetCustomerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(target));
    }

    @Test
    void filtersByPriority() throws Exception {
        String highPriority = createServiceOrder(UUID.randomUUID(), "HIGH");
        createServiceOrder(UUID.randomUUID(), "NORMAL");

        mockMvc.perform(get("/api/service-orders").param("priority", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(highPriority));
    }

    @Test
    void filtersByTechnicianIdMatchingTheDiagnosisAssignee() throws Exception {
        String serviceOrderId = createServiceOrder(UUID.randomUUID(), "NORMAL");
        String technicianId = createTechnician();
        assignDiagnosisAssignee(serviceOrderId, technicianId);
        createServiceOrder(UUID.randomUUID(), "NORMAL");

        mockMvc.perform(get("/api/service-orders").param("technicianId", technicianId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(serviceOrderId));
    }

    @Test
    void filtersByTechnicianIdMatchingAnAssignedExecution() throws Exception {
        String serviceOrderId = createServiceOrder(UUID.randomUUID(), "NORMAL");
        String executionId = diagnoseWithOneExecution(serviceOrderId);
        String technicianId = createTechnician();
        mockMvc.perform(post("/api/service-orders/{id}/executions/{executionId}/assign-technician",
                        serviceOrderId, executionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"technicianId\":\"" + technicianId + "\"}"))
                .andExpect(status().isOk());
        createServiceOrder(UUID.randomUUID(), "NORMAL");

        mockMvc.perform(get("/api/service-orders").param("technicianId", technicianId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(serviceOrderId));
    }

    @Test
    void combinesFiltersWithAnd() throws Exception {
        String matches = createServiceOrder(UUID.randomUUID(), "HIGH");
        createServiceOrder(UUID.randomUUID(), "NORMAL");
        String otherHighPriority = createServiceOrder(UUID.randomUUID(), "HIGH");
        diagnoseWithOneExecution(otherHighPriority);

        mockMvc.perform(get("/api/service-orders")
                        .param("status", "RECEIVED")
                        .param("priority", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(matches));
    }

    @Test
    void rejectsAnInvalidStatusValue() throws Exception {
        mockMvc.perform(get("/api/service-orders").param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsAnInvalidPriorityValue() throws Exception {
        mockMvc.perform(get("/api/service-orders").param("priority", "NOT_A_PRIORITY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsANonUuidCustomerId() throws Exception {
        mockMvc.perform(get("/api/service-orders").param("customerId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsANonUuidTechnicianId() throws Exception {
        mockMvc.perform(get("/api/service-orders").param("technicianId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void defaultListingExcludesCompletedAndDeliveredAndOrdersByOperationalPriority() throws Exception {
        String received = createServiceOrder(UUID.randomUUID(), "NORMAL");
        String inDiagnosis = createServiceOrder(UUID.randomUUID(), "NORMAL");
        diagnoseWithOneExecution(inDiagnosis);
        String awaitingApproval = createServiceOrder(UUID.randomUUID(), "NORMAL");
        markEstimateSentWithPendingLines(awaitingApproval);
        String inProgress = createServiceOrder(UUID.randomUUID(), "NORMAL");
        moveToInProgress(inProgress);
        String awaitingItems = createServiceOrder(UUID.randomUUID(), "NORMAL");
        moveToAwaitingItems(awaitingItems);
        String completed = createServiceOrder(UUID.randomUUID(), "NORMAL");
        moveToCompleted(completed);
        String delivered = createServiceOrder(UUID.randomUUID(), "NORMAL");
        moveToCompleted(delivered);
        finalizeServiceOrder(delivered);

        mockMvc.perform(get("/api/service-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[*].id", contains(
                        inProgress, awaitingApproval, inDiagnosis, received, awaitingItems)))
                .andExpect(jsonPath("$[*].statusLabel", contains(
                        "EXECUCAO", "AGUARDANDO_APROVACAO", "DIAGNOSTICO", "RECEBIDA", "EXECUCAO")));
    }

    @Test
    void explicitStatusFilterDisablesTheDefaultExclusion() throws Exception {
        String completed = createServiceOrder(UUID.randomUUID(), "NORMAL");
        moveToCompleted(completed);
        String delivered = createServiceOrder(UUID.randomUUID(), "NORMAL");
        moveToCompleted(delivered);
        finalizeServiceOrder(delivered);

        mockMvc.perform(get("/api/service-orders").param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(completed));

        mockMvc.perform(get("/api/service-orders").param("status", "DELIVERED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(delivered));
    }

    @Test
    void ordersServiceOrdersWithTheSameStatusFromOldestToNewest() throws Exception {
        String older = createServiceOrder(UUID.randomUUID(), "NORMAL");
        setCreatedAt(older, java.time.Instant.parse("2026-09-01T00:00:00Z"));
        String newer = createServiceOrder(UUID.randomUUID(), "NORMAL");
        setCreatedAt(newer, java.time.Instant.parse("2026-09-10T00:00:00Z"));

        mockMvc.perform(get("/api/service-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id", contains(older, newer)));
    }

    @Test
    void detailByIdIsUnaffectedByTheNewListingEndpoint() throws Exception {
        String serviceOrderId = createServiceOrder(UUID.randomUUID(), "NORMAL");

        mockMvc.perform(get("/api/service-orders/{id}", serviceOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(serviceOrderId))
                .andExpect(jsonPath("$.statusLabel").value("RECEBIDA"));

        mockMvc.perform(get("/api/service-orders/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private String createServiceOrder(UUID customerId, String priority) throws Exception {
        UUID vehicleId = UUID.randomUUID();
        ServiceOrderHttpTestFixture.persistActiveVehicle(context, customerId, vehicleId);
        String body = """
                {
                  "customerId": "%s",
                  "vehicleId": "%s",
                  "vehicleSnapshot": {"licensePlate": "ABC1D23", "brand": "Fiat", "model": "Uno", "year": 2015},
                  "priority": "%s", "initialAssessment": "Initial assessment"
                }
                """.formatted(customerId, vehicleId, priority);
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
        List<String> executionIds = JsonPath.read(result.getResponse().getContentAsString(), "$.executions[*].id");
        return executionIds.get(0);
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
        mockMvc.perform(put("/api/service-orders/{id}/diagnosis-assignee", serviceOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"technicianId\":\"" + technicianId + "\"}"))
                .andExpect(status().isOk());
    }

    /**
     * RF38 helpers below drive a ServiceOrder to each of the 7 statuses so the default listing can be
     * tested end to end. {@code markEstimateSentWithPendingLines}/{@code authorizeExecutionFromEstimate}
     * are Epic 2 domain integration points with no HTTP endpoint yet (same gap already documented in
     * {@code ServiceOrderControllerStartExecutionTest}), so they are driven directly through the
     * repository, like the rest of this test suite already does for the same reason.
     */
    private void markEstimateSentWithPendingLines(String serviceOrderId) {
        transactionTemplate.executeWithoutResult(status -> {
            ServiceOrder serviceOrder = serviceOrderRepository.findById(UUID.fromString(serviceOrderId)).orElseThrow();
            serviceOrder.markEstimateSentWithPendingLines();
            serviceOrderRepository.save(serviceOrder);
        });
    }

    private void moveToInProgress(String serviceOrderId) throws Exception {
        String executionId = diagnoseWithOneExecution(serviceOrderId);
        authorizeExecution(serviceOrderId, executionId);
        String technicianId = createTechnician();
        mockMvc.perform(post("/api/service-orders/{id}/executions/{executionId}/assign-technician",
                        serviceOrderId, executionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"technicianId\":\"" + technicianId + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/service-orders/{id}/executions/{executionId}/start", serviceOrderId, executionId))
                .andExpect(status().isOk());
    }

    private void moveToAwaitingItems(String serviceOrderId) throws Exception {
        String executionId = diagnoseWithOneExecution(serviceOrderId);
        transactionTemplate.executeWithoutResult(status -> {
            ServiceOrder serviceOrder = serviceOrderRepository.findById(UUID.fromString(serviceOrderId)).orElseThrow();
            serviceOrder.attachStockRequirement(UUID.fromString(executionId), new StockRequirement(
                    UUID.randomUUID(), StockItemType.PART, 1, "Filtro de óleo",
                    new Money(BigDecimal.TEN, "BRL"), false));
            serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), UUID.fromString(executionId));
            serviceOrderRepository.save(serviceOrder);
        });
    }

    private void moveToCompleted(String serviceOrderId) throws Exception {
        String executionId = diagnoseWithOneExecution(serviceOrderId);
        authorizeExecution(serviceOrderId, executionId);
        String technicianId = createTechnician();
        mockMvc.perform(post("/api/service-orders/{id}/executions/{executionId}/assign-technician",
                        serviceOrderId, executionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"technicianId\":\"" + technicianId + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/service-orders/{id}/executions/{executionId}/start", serviceOrderId, executionId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/service-orders/{id}/executions/{executionId}/complete", serviceOrderId, executionId))
                .andExpect(status().isOk());
    }

    private void finalizeServiceOrder(String serviceOrderId) throws Exception {
        mockMvc.perform(post("/api/service-orders/{id}/finalize", serviceOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleDelivered\": true}"))
                .andExpect(status().isOk());
    }

    private void authorizeExecution(String serviceOrderId, String executionId) {
        transactionTemplate.executeWithoutResult(status -> {
            ServiceOrder serviceOrder = serviceOrderRepository.findById(UUID.fromString(serviceOrderId)).orElseThrow();
            serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), UUID.fromString(executionId));
            serviceOrderRepository.save(serviceOrder);
        });
    }

    /**
     * Overwrites {@code created_at} directly at the persistence layer (no domain method exposes this -
     * the field is immutable once a ServiceOrder is created, by design). Used only to make the
     * "oldest first" ordering test deterministic instead of relying on wall-clock timing between two
     * sequential HTTP calls.
     */
    private void setCreatedAt(String serviceOrderId, java.time.Instant createdAt) {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createQuery("UPDATE ServiceOrderJpaEntity e SET e.createdAt = :createdAt WHERE e.id = :id")
                    .setParameter("createdAt", createdAt)
                    .setParameter("id", UUID.fromString(serviceOrderId))
                    .executeUpdate();
        });
    }
}
