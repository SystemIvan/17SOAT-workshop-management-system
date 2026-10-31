package br.com.fiap.workshop_management_system.servicelifecycle.intake.infrastructure.web;

import br.com.fiap.workshop_management_system.identity.auth.application.port.TokenIssuer;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Role;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.UserAccount;
import br.com.fiap.workshop_management_system.identity.auth.domain.model.Username;
import br.com.fiap.workshop_management_system.servicelifecycle.intake.application.dto.ServiceOrderIntakeResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.intake.application.usecase
        .OpenServiceOrderIntakeUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderStatusLabel;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .CatalogServiceArchivedForNewWorkException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .CatalogServiceNotFoundForNewWorkException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderIntakeConcurrentRegistrationException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderIntakeCustomerArchivedException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderIntakeInvalidDataException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderIntakeVehicleChassisConflictException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderIntakeVehicleOwnershipConflictException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderStockItemInactiveException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderStockItemNotFoundException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderVehicleArchivedException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Priority;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract of {@code POST /api/service-orders/intake}: validation, authorization and error translation. The use
 * case is mocked here; the end-to-end flow against the database is covered by {@code ServiceOrderIntakeFlowTest}.
 */
@SpringBootTest
class ServiceOrderIntakeControllerTest {

    private static final String ENDPOINT = "/api/service-orders/intake";
    private static final String VALID_BODY = """
            {
              "customer": {
                "document": "529.982.247-25",
                "name": "Cliente Exemplo",
                "contactInfo": { "email": "cliente@example.test", "phone": "+5511999999999" }
              },
              "vehicle": { "licensePlate": "ABC1D23", "brand": "Fiat", "model": "Argo", "year": 2022,
                           "color": "Prata" },
              "priority": "HIGH",
              "initialAssessment": "Barulho na suspensão",
              "diagnosisTechnicianId": "%s",
              "services": [
                { "catalogServiceId": "%s", "parts": [ { "stockItemId": "%s", "quantity": 2 } ] }
              ]
            }
            """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TokenIssuer tokenIssuer;

    @MockitoBean
    private OpenServiceOrderIntakeUseCase useCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void returns201WithLocationAndIntakeReferences() throws Exception {
        UUID serviceOrderId = UUID.randomUUID();
        UUID estimateId = UUID.randomUUID();
        when(useCase.execute(any())).thenReturn(new ServiceOrderIntakeResponse(
                serviceOrder(serviceOrderId),
                new ServiceOrderIntakeResponse.RegistrationReference(UUID.randomUUID(), true),
                new ServiceOrderIntakeResponse.RegistrationReference(UUID.randomUUID(), false),
                estimateId));

        mockMvc.perform(post(ENDPOINT).header("Authorization", bearer(Role.MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/service-orders/" + serviceOrderId))
                .andExpect(jsonPath("$.serviceOrder.id").value(serviceOrderId.toString()))
                .andExpect(jsonPath("$.serviceOrder.statusSnapshot").value("AWAITING_APPROVAL"))
                .andExpect(jsonPath("$.customer.created").value(true))
                .andExpect(jsonPath("$.vehicle.created").value(false))
                .andExpect(jsonPath("$.estimateId").value(estimateId.toString()));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(useCase);
    }

    @Test
    void rejectsTechnicianAndCustomerRoles() throws Exception {
        for (Role role : new Role[]{Role.TECHNICIAN, Role.CUSTOMER}) {
            mockMvc.perform(post(ENDPOINT).header("Authorization", bearer(role))
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isForbidden());
        }

        verifyNoInteractions(useCase);
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void rejectsInvalidBodiesBeforeReachingTheUseCase(String body) throws Exception {
        mockMvc.perform(post(ENDPOINT).header("Authorization", bearer(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(useCase);
    }

    static Stream<String> invalidBodies() {
        String vehicle = """
                "vehicle": { "licensePlate": "ABC1D23" }""";
        String customer = """
                "customer": { "document": "529.982.247-25" }""";
        return Stream.of(
                "{ %s, \"initialAssessment\": \"Triagem\" }".formatted(vehicle),
                "{ %s, \"initialAssessment\": \"Triagem\" }".formatted(customer),
                "{ %s, %s, \"initialAssessment\": \" \" }".formatted(customer, vehicle),
                "{ \"customer\": { \"document\": \"\" }, %s, \"initialAssessment\": \"Triagem\" }".formatted(vehicle),
                "{ %s, \"vehicle\": { \"licensePlate\": \"\" }, \"initialAssessment\": \"Triagem\" }"
                        .formatted(customer),
                ("{ %s, %s, \"initialAssessment\": \"Triagem\", \"services\": [ { \"catalogServiceId\": \"%s\", "
                        + "\"parts\": [ { \"stockItemId\": \"%s\", \"quantity\": 0 } ] } ] }")
                        .formatted(customer, vehicle, UUID.randomUUID(), UUID.randomUUID()),
                "{ %s, %s, \"initialAssessment\": \"Triagem\", \"services\": [ { } ] }".formatted(customer, vehicle),
                // Null list elements are not cascaded by @Valid; without element constraints they reach the use case.
                "{ %s, %s, \"initialAssessment\": \"Triagem\", \"services\": [ null ] }".formatted(customer, vehicle),
                ("{ %s, %s, \"initialAssessment\": \"Triagem\", \"services\": [ { \"catalogServiceId\": \"%s\", "
                        + "\"parts\": [ null ] } ] }").formatted(customer, vehicle, UUID.randomUUID()),
                ("{ \"customer\": { \"document\": \"529.982.247-25\", \"contactInfo\": { \"address\": "
                        + "{ \"state\": \"SPX\" } } }, %s, \"initialAssessment\": \"Triagem\" }").formatted(vehicle),
                "{ %s, %s, \"initialAssessment\": \"Triagem\", \"services\": [ %s ] }".formatted(customer, vehicle,
                        String.join(", ", java.util.Collections.nCopies(21,
                                "{ \"catalogServiceId\": \"%s\" }".formatted(UUID.randomUUID())))));
    }

    @ParameterizedTest
    @MethodSource("failures")
    void translatesUseCaseFailuresToStableErrorCodes(RuntimeException failure, int status, String code)
            throws Exception {
        when(useCase.execute(any())).thenThrow(failure);

        mockMvc.perform(post(ENDPOINT).header("Authorization", bearer(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code));
    }

    static Stream<Arguments> failures() {
        return Stream.of(
                Arguments.of(new ServiceOrderIntakeInvalidDataException("CPF/CNPJ inválido"), 400, "VALIDATION_ERROR"),
                Arguments.of(new NoSuchElementException("Technician not found"), 404, "NOT_FOUND"),
                Arguments.of(new CatalogServiceNotFoundForNewWorkException(), 404, "CATALOG_SERVICE_NOT_FOUND"),
                Arguments.of(new ServiceOrderStockItemNotFoundException(), 404, "STOCK_ITEM_NOT_FOUND"),
                Arguments.of(new ServiceOrderIntakeCustomerArchivedException(), 409, "CUSTOMER_ARCHIVED"),
                Arguments.of(new ServiceOrderVehicleArchivedException(), 409, "VEHICLE_ARCHIVED"),
                Arguments.of(new ServiceOrderIntakeVehicleOwnershipConflictException(), 409,
                        "VEHICLE_OWNED_BY_ANOTHER_CUSTOMER"),
                Arguments.of(new ServiceOrderIntakeVehicleChassisConflictException(), 409,
                        "VEHICLE_CHASSIS_ALREADY_EXISTS"),
                Arguments.of(new CatalogServiceArchivedForNewWorkException(), 409, "CATALOG_SERVICE_ARCHIVED"),
                Arguments.of(new ServiceOrderStockItemInactiveException(), 409, "STOCK_ITEM_INACTIVE"),
                Arguments.of(new ServiceOrderIntakeConcurrentRegistrationException(), 409,
                        "CONCURRENT_REGISTRATION"));
    }

    private String bearer(Role role) {
        UUID linkedDomainId = role == Role.TECHNICIAN || role == Role.CUSTOMER ? UUID.randomUUID() : null;
        UserAccount account = UserAccount.create(
                new Username("intake." + UUID.randomUUID()), "$2a$10$hashvalue", role, linkedDomainId,
                Instant.now());
        return "Bearer " + tokenIssuer.issue(account).token();
    }

    private static ServiceOrderResponse serviceOrder(UUID id) {
        return new ServiceOrderResponse(id, UUID.randomUUID(), UUID.randomUUID(), null, Priority.HIGH, "Triagem",
                null, ServiceOrderStatus.AWAITING_APPROVAL, ServiceOrderStatus.AWAITING_APPROVAL,
                ServiceOrderStatusLabel.AGUARDANDO_APROVACAO, Set.of(), List.of());
    }
}
