package br.com.fiap.workshop_management_system.testsupport;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public final class CatalogServiceHttpFixture {

    private CatalogServiceHttpFixture() {
    }

    public static String createActiveCatalogService(MockMvc mockMvc) throws Exception {
        String name = "Serviço fixture " + UUID.randomUUID();
        String body = """
                {
                  "name": "%s",
                  "basePrice": {"value": 100.00, "currency": "BRL"}
                }
                """.formatted(name);
        MvcResult result = mockMvc.perform(post("/api/catalog-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}
