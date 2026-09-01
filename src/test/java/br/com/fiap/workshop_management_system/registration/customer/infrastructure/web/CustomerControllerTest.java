package br.com.fiap.workshop_management_system.registration.customer.infrastructure.web;

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

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class CustomerControllerTest {

    private static final AtomicInteger CPF_SEQUENCE = new AtomicInteger(700_000_000);

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
    void createsNormalizesAndIdentifiesCustomerByFormattedCpf() throws Exception {
        String document = nextValidCpf();
        String formattedDocument = formatCpf(document);

        MvcResult result = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(formattedDocument)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/customers/")))
                .andExpect(jsonPath("$.document").value(document))
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/customers/identify").param("document", formattedDocument))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.document").value(document));
    }

    @Test
    void rejectsInvalidAndDuplicateTaxIdsAndReportsNotFound() throws Exception {
        long countBeforeInvalidRequest = repository.count();
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("123.456.789-00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CUSTOMER"))
                .andExpect(jsonPath("$.message")
                        .value("O CPF/CNPJ do cliente possui dígitos verificadores inválidos"));
        assertEquals(countBeforeInvalidRequest, repository.count());

        String document = nextValidCpf();
        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(body(document)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(formatCpf(document))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUSTOMER_TAX_ID_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message")
                        .value("Já existe um cliente com este número de identificação fiscal"));

        mockMvc.perform(get("/api/customers/identify").param("document", nextValidCpf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Cliente não encontrado"));
        mockMvc.perform(get("/api/customers/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
        mockMvc.perform(get("/api/customers/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Requisição inválida"));
    }

    private static String body(String document) {
        return """
                {
                  "name": "Cliente de Teste",
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

    private static String formatCpf(String document) {
        return document.substring(0, 3) + "." + document.substring(3, 6) + "."
                + document.substring(6, 9) + "-" + document.substring(9);
    }
}
