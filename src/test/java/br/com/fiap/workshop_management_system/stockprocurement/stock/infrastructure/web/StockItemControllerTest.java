package br.com.fiap.workshop_management_system.stockprocurement.stock.infrastructure.web;

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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class StockItemControllerTest {
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
    void createsSearchesUpdatesAndDeactivatesStockItem() throws Exception {
        String sku = "HTTP-PART-" + System.nanoTime();
        String id = create(sku, "Brake filter", "PART", 2);
        create("HTTP-SUPPLY-" + System.nanoTime(), "Brake cleaner", "SUPPLY", 0);

        mockMvc.perform(get("/api/stock-items")
                        .param("search", "brake")
                        .param("type", "PART", "SUPPLY")
                        .param("available", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id));

        String updateBody = "{\"name\":\"Premium brake filter\",\"price\":{\"value\":52.90,\"currency\":\"BRL\"}}";
        mockMvc.perform(patch("/api/stock-items/{id}", id).contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value(sku))
                .andExpect(jsonPath("$.name").value("Premium brake filter"));

        mockMvc.perform(delete("/api/stock-items/{id}", id)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/stock-items/{id}", id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/stock-items/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(patch("/api/stock-items/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Blocked\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_ITEM_INACTIVE"));
    }

    @Test
    void rejectsInvalidRequestsAndDuplicateSku() throws Exception {
        mockMvc.perform(post("/api/stock-items").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        String sku = "HTTP-DUPLICATE-" + System.nanoTime();
        create(sku, "Duplicated", "CONSUMABLE", 1);
        mockMvc.perform(post("/api/stock-items").contentType(MediaType.APPLICATION_JSON)
                        .content(body(sku, "Duplicated again", "CONSUMABLE", 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_ITEM_SKU_ALREADY_EXISTS"));
        mockMvc.perform(get("/api/stock-items/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private String create(String sku, String name, String type, int availableQuantity) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/stock-items").contentType(MediaType.APPLICATION_JSON)
                        .content(body(sku, name, type, availableQuantity)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/stock-items/")))
                .andExpect(jsonPath("$.type").value(type)).andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private static String body(String sku, String name, String type, int availableQuantity) {
        String template = "{\"sku\":\"%s\",\"name\":\"%s\",\"type\":\"%s\",\"price\":{\"value\":45.90,"
                + "\"currency\":\"BRL\"},\"availableQuantity\":%d}";
        return template.formatted(sku, name, type, availableQuantity);
    }
}
