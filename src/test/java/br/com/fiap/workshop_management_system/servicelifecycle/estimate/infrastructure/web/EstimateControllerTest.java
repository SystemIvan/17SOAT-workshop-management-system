package br.com.fiap.workshop_management_system.servicelifecycle.estimate.infrastructure.web;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.DiagnosisItem;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.VehicleSnapshot;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class EstimateControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TokenIssuer tokenIssuer;

    @Autowired
    private ServiceOrderRepository serviceOrderRepository;

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
    void generatesAndGetsEstimate() throws Exception {
        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015),
                "Initial assessment"
        );

        DiagnosisItem diagnosisItem = new DiagnosisItem(
                UUID.randomUUID(),
                "Troca de oleo",
                Money.brl(new BigDecimal("120.00")),
                List.of()
        );

        serviceOrder.assignDiagnosisAssignee(UUID.randomUUID());
        serviceOrder.performDiagnosis(List.of(diagnosisItem), UUID.randomUUID(), java.time.Instant.EPOCH);
        serviceOrderRepository.save(serviceOrder);

        UUID diagnosisId = serviceOrder.openDiagnosisId();

        String requestBody =
                "{\"diagnosisId\":\"" + diagnosisId + "\"}";

        MvcResult result = mockMvc.perform(
                        post(
                                "/api/service-orders/{serviceOrderId}/estimates",
                                serviceOrder.id()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated())
                .andExpect(
                        header().string(
                                "Location",
                                org.hamcrest.Matchers.containsString(
                                        "/api/estimates/"
                                )
                        )
                )
                .andExpect(
                        jsonPath("$.serviceOrderId")
                                .value(serviceOrder.id().toString())
                )
                .andExpect(
                        jsonPath("$.diagnosisId")
                                .value(diagnosisId.toString())
                )
                .andExpect(jsonPath("$.lines[0].serviceName")
                        .value("Troca de oleo"))
                .andExpect(jsonPath("$.lines[0].servicePrice.value")
                        .value(120.00))
                .andExpect(jsonPath("$.status").value("SENT"))
                .andReturn();

        String estimateId =
                com.jayway.jsonpath.JsonPath.read(
                        result.getResponse().getContentAsString(),
                        "$.id"
                );

        mockMvc.perform(
                        get("/api/estimates/{estimateId}", estimateId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(estimateId))
                .andExpect(
                        jsonPath("$.serviceOrderId")
                                .value(serviceOrder.id().toString())
                )
                .andExpect(jsonPath("$.status").value("SENT"));
    }

    @Test
    void rejectsRequestWithoutDiagnosisId() throws Exception {
        mockMvc.perform(
                        post(
                                "/api/service-orders/{serviceOrderId}/estimates",
                                UUID.randomUUID()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isBadRequest());
    }
}
