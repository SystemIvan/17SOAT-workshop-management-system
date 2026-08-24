package br.com.fiap.workshop_management_system.servicelifecycle.estimate.infrastructure.web;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.web
        .ServiceOrderHttpTestFixture;
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

    private record DiagnosedExecution(String executionId, String diagnosisId) {
    }

    private DiagnosedExecution diagnoseWithOneExecution(String serviceOrderId) throws Exception {
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
        String content = result.getResponse().getContentAsString();
        return new DiagnosedExecution(
                JsonPath.read(content, "$.executions[0].id"),
                JsonPath.read(content, "$.executions[0].diagnosisId"));
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
        String serviceOrderId = createServiceOrder();
        DiagnosedExecution execution = diagnoseWithOneExecution(serviceOrderId);
        String estimateId = generateEstimate(serviceOrderId, execution.diagnosisId());

        String body = """
                {"decisions":[{"serviceExecutionId":"%s","decision":"APPROVED"}]}
                """.formatted(execution.executionId());

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
