package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.web;

import br.com.fiap.workshop_management_system.identity.auth.application.port.TokenIssuer;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.persistence
        .ServiceOrderJpaRepository;
import br.com.fiap.workshop_management_system.testsupport.TestAuth;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static br.com.fiap.workshop_management_system.testsupport.CatalogServiceHttpFixture.createActiveCatalogService;
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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        serviceOrderJpaRepository.deleteAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                        .springSecurity())
                .defaultRequest(get("/").header("Authorization", "Bearer " + TestAuth.adminToken(tokenIssuer)))
                .build();
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
    void detailByIdIsUnaffectedByTheNewListingEndpoint() throws Exception {
        String serviceOrderId = createServiceOrder(UUID.randomUUID(), "NORMAL");

        mockMvc.perform(get("/api/service-orders/{id}", serviceOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(serviceOrderId));

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
}
