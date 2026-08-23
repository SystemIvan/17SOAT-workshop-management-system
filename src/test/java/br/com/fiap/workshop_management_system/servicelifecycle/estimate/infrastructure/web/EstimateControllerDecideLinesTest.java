package br.com.fiap.workshop_management_system.servicelifecycle.estimate.infrastructure.web;

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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RF15 - HTTP coverage for {@code POST /api/estimates/{estimateId}/decisions}.
 */
@SpringBootTest
class EstimateControllerDecideLinesTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
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

    private record DiagnosedExecution(String executionId, String diagnosisId) {
    }

    private DiagnosedExecution diagnoseWithOneExecution(String serviceOrderId) throws Exception {
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
        String content = result.getResponse().getContentAsString();
        return new DiagnosedExecution(
                JsonPath.read(content, "$.executions[0].id"),
                JsonPath.read(content, "$.executions[0].diagnosisId"));
    }

    private record DiagnosedExecutionPair(String firstExecutionId, String secondExecutionId, String diagnosisId) {
    }

    private DiagnosedExecutionPair diagnoseWithTwoExecutions(String serviceOrderId) throws Exception {
        String technicianId = assignDiagnosisAssignee(serviceOrderId);
        String body = """
                {
                  "diagnosedByTechnicianId": "%s",
                  "items": [
                    {"catalogServiceId": "%s", "name": "Troca de óleo", "price": {"value": 100.00, "currency": "BRL"}},
                    {"catalogServiceId": "%s", "name": "Alinhamento", "price": {"value": 80.00, "currency": "BRL"}}
                  ]
                }
                """.formatted(technicianId, UUID.randomUUID(), UUID.randomUUID());
        MvcResult result = mockMvc.perform(post("/api/service-orders/{id}/diagnosis", serviceOrderId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        String content = result.getResponse().getContentAsString();
        return new DiagnosedExecutionPair(
                JsonPath.read(content, "$.executions[0].id"),
                JsonPath.read(content, "$.executions[1].id"),
                JsonPath.read(content, "$.executions[0].diagnosisId"));
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

    private String generateEstimate(String serviceOrderId, String diagnosisId) throws Exception {
        String body = "{\"diagnosisId\":\"" + diagnosisId + "\"}";
        MvcResult result = mockMvc.perform(post("/api/service-orders/{id}/estimates", serviceOrderId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void approvesALineAndReturns200() throws Exception {
        String serviceOrderId = createServiceOrder();
        DiagnosedExecution execution = diagnoseWithOneExecution(serviceOrderId);
        String estimateId = generateEstimate(serviceOrderId, execution.diagnosisId());

        String body = """
                {"decisions":[{"serviceExecutionId":"%s","decision":"APPROVED"}]}
                """.formatted(execution.executionId());

        mockMvc.perform(post("/api/estimates/{estimateId}/decisions", estimateId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executions[0].status").value("READY"));
    }

    @Test
    void rejectsALineAndReturns200() throws Exception {
        String serviceOrderId = createServiceOrder();
        DiagnosedExecution execution = diagnoseWithOneExecution(serviceOrderId);
        String estimateId = generateEstimate(serviceOrderId, execution.diagnosisId());

        String body = """
                {"decisions":[{"serviceExecutionId":"%s","decision":"REJECTED"}]}
                """.formatted(execution.executionId());

        mockMvc.perform(post("/api/estimates/{estimateId}/decisions", estimateId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executions[0].status").value("REJECTED"));
    }

    @Test
    void returnsNotFoundWhenEstimateDoesNotExist() throws Exception {
        String body = """
                {"decisions":[{"serviceExecutionId":"%s","decision":"APPROVED"}]}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/estimates/{estimateId}/decisions", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void returnsNotFoundWhenServiceExecutionIsNotPartOfTheEstimate() throws Exception {
        String serviceOrderId = createServiceOrder();
        DiagnosedExecution execution = diagnoseWithOneExecution(serviceOrderId);
        String estimateId = generateEstimate(serviceOrderId, execution.diagnosisId());

        String body = """
                {"decisions":[{"serviceExecutionId":"%s","decision":"APPROVED"}]}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/estimates/{estimateId}/decisions", estimateId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void returnsConflictWhenServiceExecutionIsNotPending() throws Exception {
        // Two lines so the Estimate stays SENT (not CLOSED) after the first decision, isolating the
        // per-ServiceExecution PENDING check from the Estimate-status check covered by
        // returnsConflictWhenEstimateIsAlreadyClosed.
        String serviceOrderId = createServiceOrder();
        DiagnosedExecutionPair executions = diagnoseWithTwoExecutions(serviceOrderId);
        String estimateId = generateEstimate(serviceOrderId, executions.diagnosisId());

        String body = """
                {"decisions":[{"serviceExecutionId":"%s","decision":"APPROVED"}]}
                """.formatted(executions.firstExecutionId());

        mockMvc.perform(post("/api/estimates/{estimateId}/decisions", estimateId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/estimates/{estimateId}/decisions", estimateId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void returnsConflictWhenEstimateIsAlreadyClosed() throws Exception {
        String serviceOrderId = createServiceOrder();
        DiagnosedExecution execution = diagnoseWithOneExecution(serviceOrderId);
        String estimateId = generateEstimate(serviceOrderId, execution.diagnosisId());

        String body = """
                {"decisions":[{"serviceExecutionId":"%s","decision":"APPROVED"}]}
                """.formatted(execution.executionId());

        // The only line is decided, so this first call also closes the Estimate.
        mockMvc.perform(post("/api/estimates/{estimateId}/decisions", estimateId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/estimates/{estimateId}/decisions", estimateId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void returnsValidationErrorWhenDecisionsIsEmpty() throws Exception {
        String serviceOrderId = createServiceOrder();
        DiagnosedExecution execution = diagnoseWithOneExecution(serviceOrderId);
        String estimateId = generateEstimate(serviceOrderId, execution.diagnosisId());

        mockMvc.perform(post("/api/estimates/{estimateId}/decisions", estimateId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decisions\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void returnsBadRequestWhenServiceExecutionIsDuplicatedInTheSameRequest() throws Exception {
        String serviceOrderId = createServiceOrder();
        DiagnosedExecution execution = diagnoseWithOneExecution(serviceOrderId);
        String estimateId = generateEstimate(serviceOrderId, execution.diagnosisId());

        String body = """
                {"decisions":[
                  {"serviceExecutionId":"%s","decision":"APPROVED"},
                  {"serviceExecutionId":"%s","decision":"REJECTED"}
                ]}
                """.formatted(execution.executionId(), execution.executionId());

        mockMvc.perform(post("/api/estimates/{estimateId}/decisions", estimateId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }
}
