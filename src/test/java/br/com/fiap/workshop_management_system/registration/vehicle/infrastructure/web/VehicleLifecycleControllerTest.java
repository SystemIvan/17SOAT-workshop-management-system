package br.com.fiap.workshop_management_system.registration.vehicle.infrastructure.web;

import br.com.fiap.workshop_management_system.registration.customer.domain.model.ContactInfo;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.ChassisNumber;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.LicensePlate;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Mileage;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Vehicle;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.VehicleYear;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.repository.VehicleRepository;
import br.com.fiap.workshop_management_system.registration.vehicle.infrastructure.persistence.VehicleJpaEntity;
import br.com.fiap.workshop_management_system.registration.vehicle.infrastructure.persistence.VehicleJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class VehicleLifecycleControllerTest {

    private static final AtomicInteger CPF_SEQUENCE = new AtomicInteger(930_000_000);
    private static final AtomicInteger PLATE_SEQUENCE = new AtomicInteger(4000);
    private static final AtomicInteger CHASSIS_SEQUENCE = new AtomicInteger(800);

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private VehicleJpaRepository jpaRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void getsActiveAndArchivedVehiclesWithTheCompleteHistoricalRepresentation() throws Exception {
        Vehicle active = persistVehicle(true, true);
        Vehicle archived = persistVehicle(false, true);

        assertCompleteVehicle(active, true);
        assertCompleteVehicle(archived, false);
    }

    @Test
    void getRejectsMissingAndInvalidIdsWithoutExposingOperationalDetails() throws Exception {
        UUID missingId = UUID.randomUUID();

        mockMvc.perform(get("/api/vehicles/{id}", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_FOUND"))
                .andExpect(content().string(not(containsString(missingId.toString()))))
                .andExpect(content().string(not(containsString("SQLException"))));

        mockMvc.perform(get("/api/vehicles/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void listsOnlyActiveVehiclesAsCompleteNonPaginatedArray() throws Exception {
        Vehicle active = persistVehicle(true, true);
        Vehicle archived = persistVehicle(false, true);

        mockMvc.perform(get("/api/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[*].id", hasItem(active.id().toString())))
                .andExpect(jsonPath("$[*].id", not(hasItem(archived.id().toString()))))
                .andExpect(jsonPath("$[?(@.id == '%s')].customerId".formatted(active.id()),
                        hasItem(active.customerId().toString())))
                .andExpect(jsonPath("$[?(@.id == '%s')].active".formatted(active.id()), hasItem(true)));
    }

    @Test
    @Transactional
    void listReturnsAnEmptyArrayWhenNoActiveVehicleExists() throws Exception {
        jpaRepository.deleteAll();
        jpaRepository.flush();

        mockMvc.perform(get("/api/vehicles"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void archiveIsIdempotentAndPreservesTheStoredRowAndEveryBusinessField() throws Exception {
        Vehicle vehicle = persistVehicle(true, true);
        long countBefore = jpaRepository.count();

        mockMvc.perform(delete("/api/vehicles/{id}", vehicle.id()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mockMvc.perform(delete("/api/vehicles/{id}", vehicle.id()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        VehicleJpaEntity stored = jpaRepository.findById(vehicle.id()).orElseThrow();
        assertEquals(countBefore, jpaRepository.count());
        assertFalse(stored.isActive());
        assertEquals(vehicle.customerId(), stored.getCustomerId());
        assertEquals(vehicle.licensePlate().value(), stored.getLicensePlate());
        assertEquals(vehicle.chassisNumber().orElseThrow().value(), stored.getChassisNumber());
        assertEquals(vehicle.brand(), stored.getBrand());
        assertEquals(vehicle.model(), stored.getModel());
        assertEquals(vehicle.year().value(), stored.getModelYear());
        assertEquals(vehicle.color(), stored.getColor());
        assertEquals(vehicle.mileage().orElseThrow().value(), stored.getMileage());
        assertTrue(jpaRepository.existsById(vehicle.id()));

        mockMvc.perform(get("/api/vehicles/{id}", vehicle.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void archiveRejectsMissingAndInvalidIdsWithoutEchoingTheMissingIdentifier() throws Exception {
        UUID missingId = UUID.randomUUID();

        mockMvc.perform(delete("/api/vehicles/{id}", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_FOUND"))
                .andExpect(content().string(not(containsString(missingId.toString()))));

        mockMvc.perform(delete("/api/vehicles/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private void assertCompleteVehicle(Vehicle vehicle, boolean active) throws Exception {
        mockMvc.perform(get("/api/vehicles/{id}", vehicle.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(vehicle.id().toString()))
                .andExpect(jsonPath("$.customerId").value(vehicle.customerId().toString()))
                .andExpect(jsonPath("$.licensePlate").value(vehicle.licensePlate().value()))
                .andExpect(jsonPath("$.chassis").value(vehicle.chassisNumber().orElseThrow().value()))
                .andExpect(jsonPath("$.brand").value(vehicle.brand()))
                .andExpect(jsonPath("$.model").value(vehicle.model()))
                .andExpect(jsonPath("$.year").value(vehicle.year().value()))
                .andExpect(jsonPath("$.color").value(vehicle.color()))
                .andExpect(jsonPath("$.mileage").value(vehicle.mileage().orElseThrow().value()))
                .andExpect(jsonPath("$.active").value(active));
    }

    private Vehicle persistVehicle(boolean active, boolean withOptionalFields) {
        Customer customer = Customer.create("Cliente lifecycle", new TaxId(nextValidCpf()),
                new ContactInfo("lifecycle@example.test", "+5511999999999"));
        customerRepository.save(customer);
        Vehicle vehicle = Vehicle.reconstitute(
                UUID.randomUUID(),
                customer.id(),
                new LicensePlate(nextPlate()),
                withOptionalFields ? new ChassisNumber(nextChassis()) : null,
                "Volkswagen",
                "Gol",
                VehicleYear.create(2026, 2026),
                "Prata",
                withOptionalFields ? new Mileage(42_500) : null,
                active);
        vehicleRepository.save(vehicle);
        return vehicle;
    }

    private static String nextPlate() {
        return "LFC%04d".formatted(PLATE_SEQUENCE.getAndIncrement());
    }

    private static String nextChassis() {
        return "9BWZZZ377VT%06d".formatted(CHASSIS_SEQUENCE.getAndIncrement());
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
