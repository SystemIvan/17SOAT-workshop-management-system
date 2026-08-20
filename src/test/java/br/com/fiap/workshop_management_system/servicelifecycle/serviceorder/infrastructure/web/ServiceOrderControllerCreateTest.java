package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RF09 - HTTP coverage for {@code POST /api/service-orders}: creation, VehicleSnapshot freeze in the
 * response payload, default priority and input validation.
 */
@SpringBootTest
class ServiceOrderControllerCreateTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void createsAServiceOrderAndReturns201WithVehicleSnapshotAndReceivedStatus() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();

        mockMvc.perform(post("/api/service-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(customerId, vehicleId, "HIGH")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.vehicleId").value(vehicleId.toString()))
                .andExpect(jsonPath("$.vehicleSnapshot.licensePlate").value("ABC1D23"))
                .andExpect(jsonPath("$.vehicleSnapshot.brand").value("Fiat"))
                .andExpect(jsonPath("$.vehicleSnapshot.model").value("Uno"))
                .andExpect(jsonPath("$.vehicleSnapshot.year").value(2015))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.executions").isEmpty());
    }

    @Test
    void defaultsPriorityToNormalWhenNotInformed() throws Exception {
        String body = """
                {
                  "customerId": "%s",
                  "vehicleId": "%s",
                  "vehicleSnapshot": {"licensePlate": "XYZ9A87", "brand": "Ford", "model": "Ka", "year": 2020}
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/service-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("NORMAL"));
    }

    @Test
    void returnsValidationErrorWhenCustomerIdIsMissing() throws Exception {
        String body = """
                {
                  "vehicleId": "%s",
                  "vehicleSnapshot": {"licensePlate": "ABC1D23", "brand": "Fiat", "model": "Uno", "year": 2015}
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/service-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void returnsValidationErrorWhenVehicleSnapshotIsMissing() throws Exception {
        String body = """
                {
                  "customerId": "%s",
                  "vehicleId": "%s"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/service-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void returnsValidationErrorWhenVehicleSnapshotFieldsAreBlankOrInvalid() throws Exception {
        String body = """
                {
                  "customerId": "%s",
                  "vehicleId": "%s",
                  "vehicleSnapshot": {"licensePlate": "", "brand": "Fiat", "model": "Uno", "year": -1}
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/service-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private static String createBody(UUID customerId, UUID vehicleId, String priority) {
        return """
                {
                  "customerId": "%s",
                  "vehicleId": "%s",
                  "vehicleSnapshot": {"licensePlate": "ABC1D23", "brand": "Fiat", "model": "Uno", "year": 2015},
                  "priority": "%s"
                }
                """.formatted(customerId, vehicleId, priority);
    }
}
