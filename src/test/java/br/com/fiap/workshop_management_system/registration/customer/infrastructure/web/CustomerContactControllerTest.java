package br.com.fiap.workshop_management_system.registration.customer.infrastructure.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class CustomerContactControllerTest {

    private static final AtomicInteger CPF_SEQUENCE = new AtomicInteger(800_000_000);

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void createsAndPartiallyUpdatesContactWithoutChangingIdentity() throws Exception {
        String document = nextValidCpf();
        MvcResult creation = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(document, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.document").value(document))
                .andExpect(jsonPath("$.contactInfo.phone").value("+5511999998888"))
                .andExpect(jsonPath("$.contactInfo.address.state").value("SP"))
                .andExpect(jsonPath("$.contactInfo.address.postalCode").value("01310100"))
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(creation.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(patch("/api/customers/{id}/contact-info", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "document": "11222333000181",
                                  "contactInfo": {
                                    "email": "novo@example.test"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Cliente de Teste"))
                .andExpect(jsonPath("$.document").value(document))
                .andExpect(jsonPath("$.contactInfo.email").value("novo@example.test"))
                .andExpect(jsonPath("$.contactInfo.phone").value("+5511999998888"))
                .andExpect(jsonPath("$.contactInfo.address.street").value("Avenida Paulista"));

        mockMvc.perform(patch("/api/customers/{id}/contact-info", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "contactInfo": {
                                    "phone": "+351 912 345 678",
                                    "address": {
                                      "street": "Rua Augusta",
                                      "number": "500",
                                      "complement": "Apto 12",
                                      "neighborhood": "Consolação",
                                      "city": "São Paulo",
                                      "state": "sp",
                                      "postalCode": "01305-000"
                                    }
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document").value(document))
                .andExpect(jsonPath("$.contactInfo.email").value("novo@example.test"))
                .andExpect(jsonPath("$.contactInfo.phone").value("+351912345678"))
                .andExpect(jsonPath("$.contactInfo.address.street").value("Rua Augusta"))
                .andExpect(jsonPath("$.contactInfo.address.postalCode").value("01305000"));
    }

    @Test
    void rejectsEmptyNullOrInvalidContactWithoutPartialPersistence() throws Exception {
        String document = nextValidCpf();
        String id = createCustomer(document, false);

        assertInvalidPatch(id, "{\"contactInfo\":{}}", "VALIDATION_ERROR");
        assertInvalidPatch(id, "{\"contactInfo\":null}", "VALIDATION_ERROR");
        assertInvalidPatch(id, "{\"contactInfo\":{\"email\":\"novo@example.test\",\"address\":null}}",
                "VALIDATION_ERROR");
        assertInvalidPatch(id, "{\"contactInfo\":{\"phone\":\"123\"}}", "INVALID_CUSTOMER");
        assertInvalidPatch(id, """
                {
                  "contactInfo": {
                    "email": "novo@example.test",
                    "address": {
                      "street": "Rua Inválida",
                      "number": "10",
                      "city": "São Paulo",
                      "state": "XX",
                      "postalCode": "01310-100"
                    }
                  }
                }
                """, "VALIDATION_ERROR");

        mockMvc.perform(get("/api/customers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document").value(document))
                .andExpect(jsonPath("$.contactInfo.email").value("customer@example.test"))
                .andExpect(jsonPath("$.contactInfo.phone").value("+5511999998888"))
                .andExpect(jsonPath("$.contactInfo.address").value((Object) null));

        mockMvc.perform(patch("/api/customers/{id}/contact-info", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactInfo\":{\"email\":\"novo@example.test\"}}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
    }

    private void assertInvalidPatch(String id, String body, String errorCode) throws Exception {
        mockMvc.perform(patch("/api/customers/{id}/contact-info", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(errorCode));
    }

    private String createCustomer(String document, boolean withAddress) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(document, withAddress)))
                .andExpect(status().isCreated())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private static String createBody(String document, boolean withAddress) {
        String address = withAddress ? """
                ,
                    "address": {
                      "street": "Avenida Paulista",
                      "number": "1000",
                      "neighborhood": "Bela Vista",
                      "city": "São Paulo",
                      "state": "sp",
                      "postalCode": "01310-100"
                    }
                """ : "";
        return """
                {
                  "name": "Cliente de Teste",
                  "document": "%s",
                  "contactInfo": {
                    "email": "customer@example.test",
                    "phone": "(11) 99999-8888"%s
                  }
                }
                """.formatted(document, address);
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
