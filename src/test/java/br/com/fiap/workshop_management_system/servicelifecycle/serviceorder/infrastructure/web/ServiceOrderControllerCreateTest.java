package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.web;

import br.com.fiap.workshop_management_system.registration.customer.domain.model.ContactInfo;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.LicensePlate;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Vehicle;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.VehicleYear;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.repository.VehicleRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.persistence
        .ServiceOrderJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RF09 - HTTP coverage for {@code POST /api/service-orders}: creation, VehicleSnapshot freeze in the
 * response payload, default priority and input validation.
 */
@SpringBootTest
class ServiceOrderControllerCreateTest {

    private static final AtomicInteger CPF_SEQUENCE = new AtomicInteger(940_000_000);
    private static final AtomicInteger PLATE_SEQUENCE = new AtomicInteger(5000);

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private ServiceOrderJpaRepository serviceOrderJpaRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void createsAServiceOrderAndReturns201WithVehicleSnapshotAndReceivedStatus() throws Exception {
        Vehicle vehicle = persistVehicle(true);
        UUID customerId = vehicle.customerId();
        UUID vehicleId = vehicle.id();

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
        Vehicle vehicle = persistVehicle(true);
        String body = """
                {
                  "customerId": "%s",
                  "vehicleId": "%s",
                  "vehicleSnapshot": {"licensePlate": "XYZ9A87", "brand": "Ford", "model": "Ka", "year": 2020}
                }
                """.formatted(vehicle.customerId(), vehicle.id());

        mockMvc.perform(post("/api/service-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("NORMAL"));
    }

    @Test
    void rejectsMissingVehicleBeforeSavingOrExposingItsIdentifier() throws Exception {
        UUID missingVehicleId = UUID.randomUUID();
        long countBefore = serviceOrderJpaRepository.count();

        mockMvc.perform(post("/api/service-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(UUID.randomUUID(), missingVehicleId, "NORMAL")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_FOUND"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(not(containsString(missingVehicleId.toString()))));

        assertEquals(countBefore, serviceOrderJpaRepository.count());
    }

    @Test
    void rejectsArchivedVehicleBeforeSavingWithoutExposingVehicleData() throws Exception {
        Vehicle vehicle = persistVehicle(false);
        long countBefore = serviceOrderJpaRepository.count();

        mockMvc.perform(post("/api/service-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(vehicle.customerId(), vehicle.id(), "NORMAL")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VEHICLE_ARCHIVED"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(not(containsString(vehicle.id().toString()))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(not(containsString(vehicle.licensePlate().value()))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(not(containsString("constraint"))));

        assertEquals(countBefore, serviceOrderJpaRepository.count());
        assertFalse(vehicleRepository.findById(vehicle.id()).orElseThrow().active());
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

    private Vehicle persistVehicle(boolean active) {
        Customer customer = Customer.create("Cliente Service Order", new TaxId(nextValidCpf()),
                new ContactInfo("service-order@example.test", "+5511999999999"));
        customerRepository.save(customer);
        Vehicle vehicle = Vehicle.reconstitute(
                UUID.randomUUID(),
                customer.id(),
                new LicensePlate(nextPlate()),
                null,
                "Fiat",
                "Uno",
                VehicleYear.create(2015, 2026),
                "Prata",
                null,
                active);
        vehicleRepository.save(vehicle);
        return vehicle;
    }

    private static String nextPlate() {
        return "SOC%04d".formatted(PLATE_SEQUENCE.getAndIncrement());
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
