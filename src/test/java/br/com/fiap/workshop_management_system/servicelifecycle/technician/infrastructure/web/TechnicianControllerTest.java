package br.com.fiap.workshop_management_system.servicelifecycle.technician.infrastructure.web;

import br.com.fiap.workshop_management_system.identity.auth.application.port.TokenIssuer;
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

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class TechnicianControllerTest {

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
    void getsAnExistingTechnicianById() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/technicians")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana Paula\",\"specialties\":[\"ELECTRICAL\"]}"))
                .andExpect(status().isCreated())
                .andReturn();
        String technicianId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/technicians/{id}", technicianId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(technicianId))
                .andExpect(jsonPath("$.name").value("Ana Paula"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void returnsNotFoundForAnUnknownTechnicianId() throws Exception {
        mockMvc.perform(get("/api/technicians/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsAllCreatedTechnicians() throws Exception {
        mockMvc.perform(post("/api/technicians")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bruno Alves\",\"specialties\":[\"BODYWORK\"]}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/technicians"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(org.hamcrest.Matchers.greaterThanOrEqualTo(1))));
    }

    @Test
    void renamesAnExistingTechnician() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/technicians")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Carla Souza\",\"specialties\":[\"PAINTING\"]}"))
                .andExpect(status().isCreated())
                .andReturn();
        String technicianId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(patch("/api/technicians/{id}", technicianId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Carla Souza Lima\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Carla Souza Lima"));
    }

    @Test
    void rejectsRenamingAnUnknownTechnician() throws Exception {
        mockMvc.perform(patch("/api/technicians/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Novo Nome\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsCreatingATechnicianWithoutSpecialties() throws Exception {
        mockMvc.perform(post("/api/technicians")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sem Especialidade\",\"specialties\":[]}"))
                .andExpect(status().isBadRequest());
    }
}
