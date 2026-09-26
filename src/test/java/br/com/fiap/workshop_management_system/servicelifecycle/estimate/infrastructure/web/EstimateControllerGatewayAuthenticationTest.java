package br.com.fiap.workshop_management_system.servicelifecycle.estimate.infrastructure.web;

import br.com.fiap.workshop_management_system.identity.auth.application.port.TokenIssuer;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Role;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.UserAccount;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Username;
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
 * RF41 - HMAC authentication of the external estimate-approval gateway on
 * {@code POST /api/estimates/{estimateId}/decisions}, through the real security filter chain.
 *
 * <p>Rejection cases prove the use case never ran by deciding the same line afterwards with an ADMIN token: that
 * call only succeeds while the ServiceExecution is still PENDING.
 */
@SpringBootTest
class EstimateControllerGatewayAuthenticationTest {

    private static final String TIMESTAMP_HEADER = "X-Estimate-Gateway-Timestamp";
    private static final String SIGNATURE_HEADER = "X-Estimate-Gateway-Signature";

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
    void validSignatureWithoutJwtAppliesTheDecision() throws Exception {
        PendingLine line = pendingLine();
        String body = approve(line.executionId());

        mockMvc.perform(signed(line.estimateId(), body, Instant.now(), gatewaySecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executions[0].status").value("READY"));
    }

    @Test
    void validSignatureAlsoRejectsALine() throws Exception {
        PendingLine line = pendingLine();
        String body = """
                {"decisions":[{"serviceExecutionId":"%s","decision":"REJECTED"}]}
                """.formatted(line.executionId());

        mockMvc.perform(signed(line.estimateId(), body, Instant.now(), gatewaySecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executions[0].status").value("REJECTED"));
    }

    @Test
    void missingSignatureAndJwtReturns401AndLeavesTheLinePending() throws Exception {
        PendingLine line = pendingLine();
        String body = approve(line.executionId());

        mockMvc.perform(post("/api/estimates/{estimateId}/decisions", line.estimateId())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        assertStillPending(line, body);
    }

    @Test
    void wrongSignatureReturns401AndLeavesTheLinePending() throws Exception {
        PendingLine line = pendingLine();
        String body = approve(line.executionId());

        mockMvc.perform(signed(line.estimateId(), body, Instant.now(), "not-the-configured-secret"))
                .andExpect(status().isUnauthorized());

        assertStillPending(line, body);
    }

    @Test
    void expiredTimestampReturns401AndLeavesTheLinePending() throws Exception {
        PendingLine line = pendingLine();
        String body = approve(line.executionId());

        mockMvc.perform(signed(line.estimateId(), body, Instant.now().minusSeconds(301), gatewaySecret))
                .andExpect(status().isUnauthorized());

        assertStillPending(line, body);
    }

    @Test
    void customerJwtWithoutSignatureStillWorks() throws Exception {
        PendingLine line = pendingLine();

        mockMvc.perform(post("/api/estimates/{estimateId}/decisions", line.estimateId())
                        .header("Authorization", "Bearer " + tokenFor(Role.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON).content(approve(line.executionId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executions[0].status").value("READY"));
    }

    @Test
    void validJwtIsStillAcceptedWhenTheSignatureIsInvalid() throws Exception {
        PendingLine line = pendingLine();
        String body = approve(line.executionId());

        mockMvc.perform(signed(line.estimateId(), body, Instant.now(), "not-the-configured-secret")
                        .header("Authorization", "Bearer " + tokenFor(Role.CUSTOMER)))
                .andExpect(status().isOk());
    }

    @Test
    void gatewaySignatureDoesNotAuthenticateOtherRoutes() throws Exception {
        PendingLine line = pendingLine();
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        mockMvc.perform(get("/api/estimates/{estimateId}", line.estimateId())
                        .header(TIMESTAMP_HEADER, timestamp)
                        .header(SIGNATURE_HEADER, sign(gatewaySecret, timestamp, "")))
                .andExpect(status().isUnauthorized());
    }

    private void assertStillPending(PendingLine line, String body) throws Exception {
        adminMockMvc.perform(post("/api/estimates/{estimateId}/decisions", line.estimateId())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executions[0].status").value("READY"));
    }

    private MockHttpServletRequestBuilder signed(String estimateId, String body, Instant signedAt, String secret)
            throws Exception {
        String timestamp = String.valueOf(signedAt.getEpochSecond());
        return post("/api/estimates/{estimateId}/decisions", estimateId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header(TIMESTAMP_HEADER, timestamp)
                .header(SIGNATURE_HEADER, sign(secret, timestamp, body));
    }

    private static String sign(String secret, String timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
    }

    private static String approve(String executionId) {
        return """
                {"decisions":[{"serviceExecutionId":"%s","decision":"APPROVED"}]}
                """.formatted(executionId);
    }

    private String tokenFor(Role role) {
        UserAccount account = UserAccount.create(
                new Username("test." + UUID.randomUUID()), "$2a$10$hashvalue", role, UUID.randomUUID(), Instant.now());
        return tokenIssuer.issue(account).token();
    }

    private record PendingLine(String estimateId, String executionId) {
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
        return new PendingLine(JsonPath.read(estimate.getResponse().getContentAsString(), "$.id"), executionId);
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
