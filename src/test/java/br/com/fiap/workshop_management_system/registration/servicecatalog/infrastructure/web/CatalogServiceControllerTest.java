package br.com.fiap.workshop_management_system.registration.servicecatalog.infrastructure.web;

import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogService;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogServiceName;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CurrencyCode;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.Money;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.repository.CatalogServiceRepository;
import br.com.fiap.workshop_management_system.registration.servicecatalog.infrastructure.persistence
        .CatalogServiceJpaRepository;
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

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class CatalogServiceControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private CatalogServiceJpaRepository jpaRepository;

    @Autowired
    private CatalogServiceRepository repository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jpaRepository.deleteAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void createsAndGetsCatalogServiceWithCanonicalLocation() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/catalog-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("  Troca de óleo  ", "150.00")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/catalog-services/")))
                .andExpect(jsonPath("$.name").value("Troca de óleo"))
                .andExpect(jsonPath("$.basePrice.value").value(150.0))
                .andExpect(jsonPath("$.basePrice.currency").value("BRL"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.normalizedNameKey").doesNotExist())
                .andExpect(jsonPath("$.basePriceValue").doesNotExist())
                .andReturn();
        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        assertEquals("/api/catalog-services/" + id, result.getResponse().getHeader("Location"));
        mockMvc.perform(get("/api/catalog-services/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Troca de óleo"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void rejectsDuplicateNameIgnoringCaseAndExternalWhitespace() throws Exception {
        String name = "Alinhamento " + UUID.randomUUID();
        MvcResult created = mockMvc.perform(post("/api/catalog-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(name, "99.90")))
                .andExpect(status().isCreated())
                .andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/catalog-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("  " + name.toUpperCase(Locale.ROOT) + "  ", "120.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_SERVICE_NAME_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message")
                        .value("Já existe um serviço cadastrado com esse nome: " + id + " - " + name));

        assertEquals(1, jpaRepository.count());
    }

    @Test
    void preventsMassAssignmentOfIdentityAndActiveState() throws Exception {
        UUID suppliedId = UUID.randomUUID();
        String body = """
                {
                  "id": "%s",
                  "name": "Diagnóstico eletrônico",
                  "basePrice": {"value": 0, "currency": "BRL"},
                  "active": false,
                  "normalizedNameKey": "controlled-by-client"
                }
                """.formatted(suppliedId);

        mockMvc.perform(post("/api/catalog-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(not(suppliedId.toString())))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.normalizedNameKey").doesNotExist());

        assertEquals(1, jpaRepository.count());
    }

    @Test
    void listsOnlyActiveCatalogServices() throws Exception {
        CatalogService active = service("Serviço ativo", true);
        CatalogService inactive = service("Serviço arquivado", false);
        repository.save(active);
        repository.save(inactive);

        mockMvc.perform(get("/api/catalog-services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(active.id().toString()))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void returnsEmptyArrayWhenThereAreNoActiveCatalogServices() throws Exception {
        mockMvc.perform(get("/api/catalog-services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void reportsNotFoundAndSanitizesMalformedUuid() throws Exception {
        mockMvc.perform(get("/api/catalog-services/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATALOG_SERVICE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Serviço não encontrado no catálogo"));

        mockMvc.perform(get("/api/catalog-services/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Requisição inválida"));
    }

    @Test
    void rejectsInvalidNamesAndMalformedJsonWithoutPersistence() throws Exception {
        expectValidationError(validBody("   ", "10.00"));
        expectValidationError(validBody("x".repeat(256), "10.00"));
        expectValidationError("{" + "\"name\": \"Revisão\", \"basePrice\":" + "}");

        assertEquals(0, jpaRepository.count());
    }

    @Test
    void rejectsInvalidMoneyWithoutPersistence() throws Exception {
        expectValidationError(validBody("Valor negativo", "-0.01"));
        expectValidationError(validBody("Escala excessiva", "10.001"));
        expectValidationError(validBody("Precisão excessiva", "100000000000000000.00"));
        expectValidationError("{\"name\":\"Preço nulo\",\"basePrice\":null}");
        expectValidationError("{\"name\":\"Moeda nula\",\"basePrice\":{\"value\":10,\"currency\":null}}");
        expectValidationError("{\"name\":\"Moeda inválida\",\"basePrice\":{\"value\":10,\"currency\":\"USD\"}}");

        assertEquals(0, jpaRepository.count());
    }

    private void expectValidationError(String body) throws Exception {
        mockMvc.perform(post("/api/catalog-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private static String validBody(String name, String value) {
        return """
                {
                  "name": "%s",
                  "basePrice": {
                    "value": %s,
                    "currency": "BRL"
                  }
                }
                """.formatted(name, value);
    }

    private static CatalogService service(String name, boolean active) {
        return CatalogService.reconstitute(
                UUID.randomUUID(),
                new CatalogServiceName(name),
                new Money(new BigDecimal("50.00"), CurrencyCode.BRL),
                active);
    }
}
