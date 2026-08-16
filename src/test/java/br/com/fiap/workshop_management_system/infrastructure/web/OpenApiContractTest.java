package br.com.fiap.workshop_management_system.infrastructure.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class OpenApiContractTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void documentEveryCurrentHttpOperation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/customers'].post").exists())
                .andExpect(jsonPath("$.paths['/api/customers'].get").exists())
                .andExpect(jsonPath("$.paths['/api/customers/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/customers/{id}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/customers/{id}/contact-info'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/technicians'].post").exists())
                .andExpect(jsonPath("$.paths['/api/technicians'].get").exists())
                .andExpect(jsonPath("$.paths['/api/technicians/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/technicians/{id}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/technicians/{id}/status'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/stock-items'].post").exists())
                .andExpect(jsonPath("$.paths['/api/stock-items'].get").exists())
                .andExpect(jsonPath("$.paths['/api/stock-items/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/stock-items/{id}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/stock-items/{id}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/parts']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/service-orders'].post").exists())
                .andExpect(jsonPath("$.paths['/api/service-orders/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/service-orders/{id}/status'].get").exists())
                .andExpect(jsonPath("$.paths['/api/service-orders/{id}/diagnosis'].post").exists())
                .andExpect(jsonPath("$.paths['/api/service-orders/{id}/executions/{executionId}/assign-technician'].post")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/service-orders/{id}/executions/{executionId}/start'].post")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/service-orders/{id}/executions/{executionId}/progress'].patch")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/service-orders/{id}/executions/{executionId}/complete'].post")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/service-orders/{id}/finalize'].post").exists())
                .andExpect(jsonPath("$.paths['/api/service-orders/{serviceOrderId}/estimates'].post").exists())
                .andExpect(jsonPath("$.paths['/api/estimates/{estimateId}'].get").exists());
    }
}
