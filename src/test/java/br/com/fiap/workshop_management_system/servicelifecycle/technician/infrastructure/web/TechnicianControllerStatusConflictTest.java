package br.com.fiap.workshop_management_system.servicelifecycle.technician.infrastructure.web;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms {@code ServiceLifecycleExceptionHandler} covers {@link TechnicianController} too, not only
 * {@code ServiceOrderController} - both live under {@code servicelifecycle} and both throw plain
 * {@link IllegalStateException} for state conflicts.
 */
@SpringBootTest
class TechnicianControllerStatusConflictTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TokenIssuer tokenIssuer;

    private MockMvc mockMvc;

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
    void returnsConflictWhenChangingStatusOfAnInactiveTechnician() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/technicians")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Carlos Silva\",\"specialties\":[\"MECHANICAL\"]}"))
                .andExpect(status().isCreated())
                .andReturn();
        String technicianId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(patch("/api/technicians/{id}/status", technicianId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/technicians/{id}/status", technicianId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BUSY\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }
}
