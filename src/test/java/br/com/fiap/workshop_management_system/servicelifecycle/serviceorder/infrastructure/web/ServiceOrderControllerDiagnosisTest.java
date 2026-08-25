package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.web;

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
import br.com.fiap.workshop_management_system.identity.auth.application.port.TokenIssuer;
import br.com.fiap.workshop_management_system.testsupport.TestAuth;

import java.util.UUID;

import static br.com.fiap.workshop_management_system.testsupport.CatalogServiceHttpFixture
        .createActiveCatalogService;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RF11 - HTTP coverage for {@code POST /api/service-orders/{id}/diagnosis}.
 */
@SpringBootTest
class ServiceOrderControllerDiagnosisTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TokenIssuer tokenIssuer;

    private MockMvc mockMvc;
    private String diagnosedByTechnicianId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                        .springSecurity())
                .defaultRequest(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/").header("Authorization", "Bearer " + TestAuth.adminToken(tokenIssuer)))
                .build();
    }

    @Test
    void recordsDiagnosisAndReturns200() throws Exception {
        String serviceOrderId = createServiceOrder();

        String body = """
                {
                  "diagnosedByTechnicianId": "%s",
                  "items": [
                    {"catalogServiceId": "%s", "name": "Troca de óleo", "price": {"value": 100.00, "currency": "BRL"}},
                    {"catalogServiceId": "%s", "name": "Alinhamento", "price": {"value": 80.00, "currency": "BRL"}}
                  ]
                }
                """.formatted(
                        diagnosedByTechnicianId,
                        createActiveCatalogService(mockMvc),
                        createActiveCatalogService(mockMvc));

        mockMvc.perform(post("/api/service-orders/{id}/diagnosis", serviceOrderId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executions.length()").value(2))
                .andExpect(jsonPath("$.executions[0].name").value("Troca de óleo"))
                .andExpect(jsonPath("$.executions[0].diagnosedByTechnicianId").value(diagnosedByTechnicianId))
                .andExpect(jsonPath("$.executions[0].diagnosedAt").exists())
                .andExpect(jsonPath("$.executions[1].diagnosedByTechnicianId").value(diagnosedByTechnicianId))
                .andExpect(jsonPath("$.executions[1].diagnosedAt").exists());
    }

    @Test
    void returnsNotFoundWhenServiceOrderDoesNotExist() throws Exception {
        String body = """
                {
                  "diagnosedByTechnicianId": "%s",
                  "items": [
                    {"catalogServiceId": "%s", "name": "Troca de óleo", "price": {"value": 100.00, "currency": "BRL"}}
                  ]
                }
                """.formatted(createTechnician(), UUID.randomUUID());

        mockMvc.perform(post("/api/service-orders/{id}/diagnosis", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void returnsValidationErrorWhenItemsIsEmpty() throws Exception {
        String serviceOrderId = createServiceOrder();

        mockMvc.perform(post("/api/service-orders/{id}/diagnosis", serviceOrderId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void returnsConflictWhenADiagnosisIsAlreadyOpen() throws Exception {
        String serviceOrderId = createServiceOrder();
        String body = """
                {
                  "diagnosedByTechnicianId": "%s",
                  "items": [
                    {"catalogServiceId": "%s", "name": "Troca de óleo", "price": {"value": 100.00, "currency": "BRL"}}
                  ]
                }
                """.formatted(diagnosedByTechnicianId, createActiveCatalogService(mockMvc));

        mockMvc.perform(post("/api/service-orders/{id}/diagnosis", serviceOrderId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/service-orders/{id}/diagnosis", serviceOrderId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void rejectsArchivedCatalogServiceWithoutCreatingExecutions() throws Exception {
        String serviceOrderId = createServiceOrder();
        String catalogServiceId = createActiveCatalogService(mockMvc);
        mockMvc.perform(delete("/api/catalog-services/{id}", catalogServiceId))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/service-orders/{id}/diagnosis", serviceOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(diagnosisBody(catalogServiceId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_SERVICE_ARCHIVED"))
                .andExpect(jsonPath("$.message")
                        .value("Serviço arquivado não pode ser utilizado em novos trabalhos"));

        mockMvc.perform(get("/api/service-orders/{id}", serviceOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executions.length()").value(0));
    }

    @Test
    void rejectsMissingCatalogServiceWithoutCreatingExecutions() throws Exception {
        String serviceOrderId = createServiceOrder();

        mockMvc.perform(post("/api/service-orders/{id}/diagnosis", serviceOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(diagnosisBody(UUID.randomUUID().toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATALOG_SERVICE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Serviço não encontrado no catálogo"));

        mockMvc.perform(get("/api/service-orders/{id}", serviceOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executions.length()").value(0));
    }

    @Test
    void existingExecutionContinuesUsingItsSnapshotAfterCatalogArchive() throws Exception {
        String serviceOrderId = createServiceOrder();
        String catalogServiceId = createActiveCatalogService(mockMvc);
        MvcResult diagnosis = mockMvc.perform(post("/api/service-orders/{id}/diagnosis", serviceOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(diagnosisBody(catalogServiceId)))
                .andExpect(status().isOk())
                .andReturn();
        String executionId = JsonPath.read(diagnosis.getResponse().getContentAsString(), "$.executions[0].id");

        mockMvc.perform(delete("/api/catalog-services/{id}", catalogServiceId))
                .andExpect(status().isNoContent());

        String requirement = """
                {
                  "stockItemId": "%s",
                  "type": "PART",
                  "quantity": 1,
                  "nameSnapshot": "Filtro de óleo",
                  "priceSnapshot": {"value": 25.00, "currency": "BRL"}
                }
                """.formatted(UUID.randomUUID());
        mockMvc.perform(post("/api/service-orders/{id}/executions/{executionId}/stock-requirements",
                        serviceOrderId, executionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requirement))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executions[0].catalogServiceId").value(catalogServiceId))
                .andExpect(jsonPath("$.executions[0].name").value("Troca de óleo"))
                .andExpect(jsonPath("$.executions[0].stockRequirements.length()").value(1));
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
                  "priority": "NORMAL", "initialAssessment": "Initial assessment"
                }
                """.formatted(customerId, vehicleId);
        MvcResult result = mockMvc.perform(post("/api/service-orders")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        String serviceOrderId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        diagnosedByTechnicianId = assignDiagnosisAssignee(serviceOrderId);
        return serviceOrderId;
    }

    private String assignDiagnosisAssignee(String serviceOrderId) throws Exception {
        String technicianId = createTechnician();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/service-orders/{id}/diagnosis-assignee", serviceOrderId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"technicianId\":\"" + technicianId + "\"}"))
                .andExpect(status().isOk());
        return technicianId;
    }

    private String createTechnician() throws Exception {
        MvcResult technician = mockMvc.perform(post("/api/technicians").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Carlos Silva\",\"specialties\":[\"MECHANICAL\"]}"))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(technician.getResponse().getContentAsString(), "$.id");
    }

    private String diagnosisBody(String catalogServiceId) {
        return """
                {
                  "diagnosedByTechnicianId": "%s",
                  "items": [
                    {"catalogServiceId": "%s", "name": "Troca de óleo",
                     "price": {"value": 100.00, "currency": "BRL"}}
                  ]
                }
                """.formatted(diagnosedByTechnicianId, catalogServiceId);
    }
}
