package br.com.fiap.workshop_management_system.servicelifecycle.estimate.infrastructure.web;

import br.com.fiap.workshop_management_system.identity.auth.application.port.TokenIssuer;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.web
        .ServiceOrderHttpTestFixture;
import br.com.fiap.workshop_management_system.testsupport.TestAuth;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static br.com.fiap.workshop_management_system.testsupport.CatalogServiceHttpFixture
        .createActiveCatalogService;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RF40 - {@code POST /api/service-orders/{serviceOrderId}/external-status-updates} through the real security
 * filter chain and the real flow up to a SENT estimate.
 *
 * <p>Rejection cases prove nothing changed by deciding the same line afterwards through the internal channel
 * with an ADMIN token: that call only succeeds while the ServiceExecution is still PENDING.
 */
@SpringBootTest
class ExternalStatusUpdateControllerTest {

    private static final String PATH = "/api/service-orders/{serviceOrderId}/external-status-updates";
    private static final String APPROVED = "{\"intendedStatus\":\"APPROVED\"}";
    private static final String REJECTED = "{\"intendedStatus\":\"REJECTED\"}";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TokenIssuer tokenIssuer;

    @Value("${app.security.estimate-gateway.hmac-secret}")
    private String gatewaySecret;

    private MockMvc adminMockMvc;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        adminMockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .defaultRequest(get("/").header("Authorization", "Bearer " + TestAuth.adminToken(tokenIssuer)))
                .build();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void approvedIntentAuthorizesThePendingLine() throws Exception {
        PendingLine line = pendingLine();

        mockMvc.perform(signed(line.serviceOrderId(), APPROVED, gatewaySecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(line.serviceOrderId()))
                .andExpect(jsonPath("$.executions[0].status").value("READY"));
    }

    @Test
    void rejectedIntentRejectsThePendingLine() throws Exception {
        PendingLine line = pendingLine();

        mockMvc.perform(signed(line.serviceOrderId(), REJECTED, gatewaySecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executions[0].status").value("REJECTED"));
    }

    @Test
    void resendingTheSameIntentAfterItWasAppliedIsAConflict() throws Exception {
        PendingLine line = pendingLine();
        mockMvc.perform(signed(line.serviceOrderId(), APPROVED, gatewaySecret)).andExpect(status().isOk());

        mockMvc.perform(signed(line.serviceOrderId(), APPROVED, gatewaySecret))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void serviceOrderStillInDiagnosisIsAConflict() throws Exception {
        String serviceOrderId = createServiceOrder();

        mockMvc.perform(signed(serviceOrderId, APPROVED, gatewaySecret))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void estimateAlreadyDecidedThroughTheInternalChannelIsAConflict() throws Exception {
        PendingLine line = pendingLine();
        decideInternally(line).andExpect(status().isOk());

        mockMvc.perform(signed(line.serviceOrderId(), REJECTED, gatewaySecret))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void unknownServiceOrderIsNotFound() throws Exception {
        mockMvc.perform(signed(UUID.randomUUID().toString(), APPROVED, gatewaySecret))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void unsupportedIntendedStatusIsAValidationError() throws Exception {
        PendingLine line = pendingLine();

        mockMvc.perform(signed(line.serviceOrderId(), "{\"intendedStatus\":\"EXECUCAO\"}", gatewaySecret))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertStillPending(line);
    }

    @Test
    void missingIntendedStatusIsAValidationError() throws Exception {
        PendingLine line = pendingLine();

        mockMvc.perform(signed(line.serviceOrderId(), "{}", gatewaySecret))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void callWithoutSignatureIsUnauthorizedAndChangesNothing() throws Exception {
        PendingLine line = pendingLine();

        mockMvc.perform(post(PATH, line.serviceOrderId()).contentType(MediaType.APPLICATION_JSON).content(APPROVED))
                .andExpect(status().isUnauthorized());

        assertStillPending(line);
    }

    @Test
    void wrongSignatureIsUnauthorizedAndChangesNothing() throws Exception {
        PendingLine line = pendingLine();

        mockMvc.perform(signed(line.serviceOrderId(), APPROVED, "not-the-configured-secret"))
                .andExpect(status().isUnauthorized());

        assertStillPending(line);
    }

    @Test
    void adminJwtWithoutSignatureIsForbiddenAndChangesNothing() throws Exception {
        PendingLine line = pendingLine();

        mockMvc.perform(post(PATH, line.serviceOrderId())
                        .header("Authorization", "Bearer " + TestAuth.adminToken(tokenIssuer))
                        .contentType(MediaType.APPLICATION_JSON).content(APPROVED))
                .andExpect(status().isForbidden());

        assertStillPending(line);
    }

    private void assertStillPending(PendingLine line) throws Exception {
        decideInternally(line)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executions[0].status").value("READY"));
    }

    private ResultActions decideInternally(PendingLine line) throws Exception {
        String body = """
                {"decisions":[{"serviceExecutionId":"%s","decision":"APPROVED"}]}
                """.formatted(line.executionId());
        return adminMockMvc.perform(post("/api/estimates/{estimateId}/decisions", line.estimateId())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private MockHttpServletRequestBuilder signed(String serviceOrderId, String body, String secret) throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        return post(PATH, serviceOrderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("X-Estimate-Gateway-Timestamp", timestamp)
                .header("X-Estimate-Gateway-Signature", sign(secret, timestamp, body));
    }

    private static String sign(String secret, String timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
    }

    private record PendingLine(String serviceOrderId, String estimateId, String executionId) {
    }

    private PendingLine pendingLine() throws Exception {
        String serviceOrderId = createServiceOrder();
        MvcResult technician = adminMockMvc.perform(post("/api/technicians").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Carlos Silva\",\"specialties\":[\"MECHANICAL\"]}"))
                .andExpect(status().isCreated()).andReturn();
        String technicianId = JsonPath.read(technician.getResponse().getContentAsString(), "$.id");
        adminMockMvc.perform(put("/api/service-orders/{id}/diagnosis-assignee", serviceOrderId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"technicianId\":\"" + technicianId + "\"}"))
                .andExpect(status().isOk());

        String diagnosisBody = """
                {
                  "diagnosedByTechnicianId": "%s",
                  "items": [
                    {"catalogServiceId": "%s", "name": "Troca de óleo", "price": {"value": 100.00, "currency": "BRL"}}
                  ]
                }
                """.formatted(technicianId, createActiveCatalogService(adminMockMvc));
        MvcResult diagnosis = adminMockMvc.perform(post("/api/service-orders/{id}/diagnosis", serviceOrderId)
                        .contentType(MediaType.APPLICATION_JSON).content(diagnosisBody))
                .andExpect(status().isOk())
                .andReturn();
        String diagnosisContent = diagnosis.getResponse().getContentAsString();
        String executionId = JsonPath.read(diagnosisContent, "$.executions[0].id");
        String diagnosisId = JsonPath.read(diagnosisContent, "$.executions[0].diagnosisId");

        MvcResult estimate = adminMockMvc.perform(post("/api/service-orders/{id}/estimates", serviceOrderId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"diagnosisId\":\"" + diagnosisId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String estimateId = JsonPath.read(estimate.getResponse().getContentAsString(), "$.id");
        return new PendingLine(serviceOrderId, estimateId, executionId);
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
        MvcResult result = adminMockMvc.perform(post("/api/service-orders")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}
