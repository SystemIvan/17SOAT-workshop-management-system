package br.com.fiap.workshop_management_system.registration.customer.infrastructure.web;

import br.com.fiap.workshop_management_system.registration.customer.infrastructure.persistence.CustomerJpaEntity;
import br.com.fiap.workshop_management_system.registration.customer.infrastructure.persistence.CustomerJpaRepository;
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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class CustomerLifecycleControllerTest {

    private static final AtomicInteger CPF_SEQUENCE = new AtomicInteger(900_000_000);

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TokenIssuer tokenIssuer;

    @Autowired
    private CustomerJpaRepository repository;

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
    void archivesIdempotentlyAndPreservesOnlyHistoricalAccess() throws Exception {
        String document = nextValidCpf();
        MvcResult creation = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(document)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(creation.getResponse().getContentAsString(), "$.id");
        long rowCount = repository.count();

        mockMvc.perform(delete("/api/customers/{id}", id))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mockMvc.perform(delete("/api/customers/{id}", id))
                .andExpect(status().isNoContent());

        assertEquals(rowCount, repository.count());
        CustomerJpaEntity persisted = repository.findById(UUID.fromString(id)).orElseThrow();
        assertFalse(persisted.isActive());

        mockMvc.perform(get("/api/customers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.document").value(document))
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get("/api/customers/identify").param("document", document))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')]").isEmpty());

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(document)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUSTOMER_TAX_ID_ALREADY_EXISTS"));
        mockMvc.perform(patch("/api/customers/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Novo Nome\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUSTOMER_ARCHIVED"));
        mockMvc.perform(patch("/api/customers/{id}/contact-info", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactInfo\":{\"email\":\"novo@example.test\"}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUSTOMER_ARCHIVED"));
    }

    @Test
    void archiveReportsNotFoundForUnknownId() throws Exception {
        mockMvc.perform(delete("/api/customers/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
    }

    private static String createBody(String document) {
        return """
                {
                  "name": "Cliente de Lifecycle",
                  "document": "%s",
                  "contactInfo": {
                    "email": "customer@example.test",
                    "phone": "+5511999999999"
                  }
                }
                """.formatted(document);
    }

    private static String nextValidCpf() {
        String base = "%09d".formatted(CPF_SEQUENCE.getAndIncrement());
        int firstCheckDigit = calculateCpfCheckDigit(base);
        String partialCpf = base + firstCheckDigit;
        return partialCpf + calculateCpfCheckDigit(partialCpf);
    }

    private static int calculateCpfCheckDigit(String digits) {
        int sum = 0;
        for (int index = 0; index < digits.length(); index++) {
            sum += (digits.charAt(index) - '0') * (digits.length() + 1 - index);
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }
}
