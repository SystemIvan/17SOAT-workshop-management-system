package br.com.fiap.workshop_management_system.infrastructure.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.hasItem;
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
                .andExpect(jsonPath("$.paths['/api/customers/identify'].get").exists())
                .andExpect(jsonPath("$.paths['/api/customers/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/customers/{id}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/customers/{id}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/customers/{id}/contact-info'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/vehicles'].post").exists())
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
                .andExpect(jsonPath(
                        "$.paths['/api/service-orders/{id}/executions/{executionId}/assign-technician'].post")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/service-orders/{id}/executions/{executionId}/start'].post")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/service-orders/{id}/executions/{executionId}/progress'].patch")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/service-orders/{id}/executions/{executionId}/complete'].post")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/service-orders/{id}/finalize'].post").exists());
    }

    @Test
    void documentPartialCustomerContactUpdateContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/customers/{id}/contact-info'].patch.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/customers/{id}/contact-info'].patch.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/customers/{id}/contact-info'].patch.responses['404']").exists())
                .andExpect(jsonPath("$.components.schemas.UpdateCustomerContactRequest.properties.contactInfo")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.UpdateContactInfoDTO.properties.email").exists())
                .andExpect(jsonPath("$.components.schemas.UpdateContactInfoDTO.properties.phone").exists())
                .andExpect(jsonPath("$.components.schemas.UpdateContactInfoDTO.properties.address").exists())
                .andExpect(jsonPath("$.components.schemas.ContactInfoDTO.properties.address").exists())
                .andExpect(jsonPath("$.components.schemas.AddressDTO.properties.street").exists())
                .andExpect(jsonPath("$.components.schemas.AddressDTO.properties.state").exists())
                .andExpect(jsonPath("$.components.schemas.AddressDTO.properties.postalCode").exists());
    }

    @Test
    void documentCustomerLifecycleContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/customers/{id}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/customers/{id}'].delete.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/customers/{id}'].patch.responses['409']").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/customers/{id}/contact-info'].patch.responses['409']").exists())
                .andExpect(jsonPath("$.components.schemas.CustomerResponse.properties.active").exists());
    }

    @Test
    void documentVehicleRegistrationContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/vehicles'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/vehicles'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/vehicles'].post.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/vehicles'].post.responses['409']").exists())
                .andExpect(jsonPath("$.components.schemas.CreateVehicleRequest.properties.customerId").exists())
                .andExpect(jsonPath("$.components.schemas.CreateVehicleRequest.properties.chassis").exists())
                .andExpect(jsonPath("$.components.schemas.CreateVehicleRequest.properties.chassis.type",
                        hasItem("null")))
                .andExpect(jsonPath("$.components.schemas.VehicleResponse.properties.id").exists())
                .andExpect(jsonPath("$.components.schemas.VehicleResponse.properties.vehicleId").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.VehicleResponse.properties.customerId").exists())
                .andExpect(jsonPath("$.components.schemas.VehicleResponse.properties.chassis").exists())
                .andExpect(jsonPath("$.components.schemas.VehicleResponse.properties.chassis.type",
                        hasItem("null")))
                .andExpect(jsonPath("$.components.schemas.VehicleResponse.properties.active").exists());
    }
}
