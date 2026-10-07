package br.com.fiap.workshop_management_system.identity;

import br.com.fiap.workshop_management_system.identity.auth.application.port.TokenIssuer;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Role;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.UserAccount;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Username;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ActuatorSecurityTest {

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

    @Test
    void allowsAnonymousGetOnHealthAndProbes() throws Exception {
        String[] paths = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"};
        for (String path : paths) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.components").doesNotExist())
                    .andExpect(jsonPath("$.details").doesNotExist());
        }
    }

    @Test
    void deniesAnonymousAccessToOtherActuatorEndpoints() throws Exception {
        mockMvc.perform(get("/actuator"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/health/db"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deniesOtherActuatorEndpointsEvenWithAValidAdminToken() throws Exception {
        // The actuator chain has no JWT filter, so a real ADMIN token is ignored and the caller stays anonymous.
        String adminToken = "Bearer " + tokenFor(Role.ADMIN);

        mockMvc.perform(get("/actuator").header("Authorization", adminToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/env").header("Authorization", adminToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/health/db").header("Authorization", adminToken))
                .andExpect(status().isUnauthorized());
    }

    private String tokenFor(Role role) {
        UserAccount account = UserAccount.create(
                new Username("actuator." + UUID.randomUUID()), "$2a$10$hashvalue", role, null, Instant.now());
        return tokenIssuer.issue(account).token();
    }

    @Test
    void deniesPostPutDeleteOnProbes() throws Exception {
        String[] paths = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"};
        for (String path : paths) {
            mockMvc.perform(post(path))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(put(path))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(delete(path))
                    .andExpect(status().isUnauthorized());
        }
    }
}
