package br.com.fiap.workshop_management_system;

import br.com.fiap.workshop_management_system.identity.auth.application.port.TokenIssuer;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Role;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.UserAccount;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Username;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the authorization matrix from technical-spec.md (jwt-authentication) end to end through
 * the real SecurityFilterChain — unlike the existing per-controller MockMvcTest suites, which build
 * MockMvc without {@code springSecurity()} and therefore never go through this filter chain at all.
 * Retrofitting those existing suites with authentication happens in a later checkpoint; this class is
 * the dedicated coverage for the security layer itself.
 */
@SpringBootTest
class SecurityAuthorizationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TokenIssuer tokenIssuer;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private String tokenFor(Role role, UUID linkedDomainId) {
        UserAccount account = UserAccount.create(
                new Username("test." + UUID.randomUUID()), "$2a$10$hashvalue", role, linkedDomainId, Instant.now());
        return tokenIssuer.issue(account).token();
    }

    @Test
    void rejectsAnAdministrativeEndpointWithoutAToken() throws Exception {
        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAnAdministrativeEndpointWithAnUnauthorizedRole() throws Exception {
        String token = tokenFor(Role.TECHNICIAN, UUID.randomUUID());

        mockMvc.perform(get("/api/customers").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsAnAdministrativeEndpointWithAnAuthorizedRole() throws Exception {
        String token = tokenFor(Role.MANAGER, null);

        mockMvc.perform(get("/api/customers").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void purchaseOrderEndpointsRejectRequestsWithoutAToken() throws Exception {
        mockMvc.perform(get("/api/purchase-demands"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/purchase-orders/" + UUID.randomUUID() + "/receipt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void purchaseOrderEndpointsRejectNonManagerRoles() throws Exception {
        String token = tokenFor(Role.TECHNICIAN, UUID.randomUUID());

        mockMvc.perform(get("/api/purchase-demands")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/purchase-orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/purchase-orders/" + UUID.randomUUID() + "/receipt")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void purchaseOrderEndpointsAllowManagerRole() throws Exception {
        String token = tokenFor(Role.MANAGER, null);

        mockMvc.perform(get("/api/purchase-demands")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/purchase-orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/purchase-orders/" + UUID.randomUUID() + "/receipt")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void loginEndpointIsReachableWithoutAToken() throws Exception {
        // An unknown username fails business validation (401 INVALID_CREDENTIALS from
        // AuthExceptionHandler), which is only reachable if the security layer let the request
        // through in the first place instead of blocking it with its own 401 (UNAUTHORIZED).
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"unknown-" + UUID.randomUUID() + "\", \"password\": \"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                        .value("INVALID_CREDENTIALS"));
    }

    @Test
    void serviceOrderTrackingStatusAcceptsAnyAuthenticatedRoleIncludingCustomer() throws Exception {
        String token = tokenFor(Role.CUSTOMER, UUID.randomUUID());

        // The service order does not exist, but reaching the controller (404, not 401/403) is what
        // this test verifies about the security layer.
        mockMvc.perform(get("/api/service-orders/" + UUID.randomUUID() + "/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void serviceOrderCreationRejectsCustomerRole() throws Exception {
        String token = tokenFor(Role.CUSTOMER, UUID.randomUUID());

        mockMvc.perform(post("/api/service-orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void serviceOrderListingRejectsCustomerRole() throws Exception {
        String token = tokenFor(Role.CUSTOMER, UUID.randomUUID());

        mockMvc.perform(get("/api/service-orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void averageExecutionTimeMetricAllowsOnlyManagerAndAdmin() throws Exception {
        String endpoint = "/api/service-orders/metrics/average-execution-time";

        mockMvc.perform(get(endpoint)
                        .param("from", "2026-08-01T00:00:00Z")
                        .param("to", "2026-09-01T00:00:00Z"))
                .andExpect(status().isUnauthorized());

        for (Role role : new Role[]{Role.CUSTOMER, Role.TECHNICIAN}) {
            mockMvc.perform(get(endpoint)
                            .header("Authorization", "Bearer " + tokenFor(role, UUID.randomUUID()))
                            .param("from", "2026-08-01T00:00:00Z")
                            .param("to", "2026-09-01T00:00:00Z"))
                    .andExpect(status().isForbidden());
        }

        for (Role role : new Role[]{Role.MANAGER, Role.ADMIN}) {
            mockMvc.perform(get(endpoint)
                            .header("Authorization", "Bearer " + tokenFor(role, null))
                            .param("from", "2026-08-01T00:00:00Z")
                            .param("to", "2026-09-01T00:00:00Z"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void estimateGenerationRejectsTechnicianRole() throws Exception {
        String token = tokenFor(Role.TECHNICIAN, UUID.randomUUID());

        mockMvc.perform(post("/api/service-orders/" + UUID.randomUUID() + "/estimates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void estimateDecisionsAllowsCustomerRole() throws Exception {
        String token = tokenFor(Role.CUSTOMER, UUID.randomUUID());

        // An empty body fails request validation (400), but that happens only after the security
        // layer already let the CUSTOMER role through — a 401/403 here would mean the role was
        // wrongly rejected before validation ever ran.
        mockMvc.perform(post("/api/estimates/" + UUID.randomUUID() + "/decisions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void estimateDecisionsRejectsTechnicianRole() throws Exception {
        String token = tokenFor(Role.TECHNICIAN, UUID.randomUUID());

        mockMvc.perform(post("/api/estimates/" + UUID.randomUUID() + "/decisions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsAnExpiredOrTamperedToken() throws Exception {
        mockMvc.perform(get("/api/customers").header("Authorization", "Bearer not-a-valid-jwt"))
                .andExpect(status().isUnauthorized());
    }
}
