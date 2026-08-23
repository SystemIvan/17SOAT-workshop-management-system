package br.com.fiap.workshop_management_system.registration.vehicle.infrastructure.web;

import br.com.fiap.workshop_management_system.registration.customer.domain.model.ContactInfo;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.LicensePlate;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Mileage;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Vehicle;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.VehicleYear;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.repository.VehicleRepository;
import br.com.fiap.workshop_management_system.registration.vehicle.infrastructure.persistence.VehicleJpaRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class VehicleMileageControllerTest {

    private static final AtomicInteger CPF_SEQUENCE = new AtomicInteger(300_000_000);
    private static final AtomicInteger PLATE_SEQUENCE = new AtomicInteger(3000);

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
    void recordsFirstGreaterAndEqualMileageWhilePreservingVehicleData() throws Exception {
        Vehicle vehicle = persistVehicle(true, null);

        updateMileage(vehicle.id(), "42500")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mileage").value(42_500))
                .andExpect(jsonPath("$.licensePlate").value(vehicle.licensePlate().value()))
                .andExpect(jsonPath("$.brand").value("Volkswagen"))
                .andExpect(jsonPath("$.model").value("Gol"));

        updateMileage(vehicle.id(), "43000")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mileage").value(43_000));

        updateMileage(vehicle.id(), "43000")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mileage").value(43_000));

        assertEquals(43_000L, jpaRepository.findById(vehicle.id()).orElseThrow().getMileage());
    }

    @Test
    void rejectsMileageDecreaseWithoutChangingStoredValue() throws Exception {
        Vehicle vehicle = persistVehicle(true, 42_500L);

        updateMileage(vehicle.id(), "42499")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VEHICLE_MILEAGE_CANNOT_DECREASE"));

        assertEquals(42_500L, jpaRepository.findById(vehicle.id()).orElseThrow().getMileage());
    }

    @Test
    void rejectsMissingAndArchivedVehicle() throws Exception {
        updateMileage(UUID.randomUUID(), "1")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_FOUND"));

        Vehicle archived = persistVehicle(false, null);
        updateMileage(archived.id(), "1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VEHICLE_ARCHIVED"));
    }

    @Test
    void rejectsMissingNullNegativeFractionalCoercedAndOverflowMileage() throws Exception {
        Vehicle vehicle = persistVehicle(true, null);
        String[] invalidBodies = {
                "{}",
                "{\"mileage\":null}",
                "{\"mileage\":-1}",
                "{\"mileage\":1.5}",
                "{\"mileage\":\"42500\"}",
                "{\"mileage\":true}",
                "{\"mileage\":[]}",
                "{\"mileage\":{}}",
                "{\"mileage\":9223372036854775808}"
        };

        for (String body : invalidBodies) {
            mockMvc.perform(patch("/api/vehicles/{id}/mileage", vehicle.id())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void rejectsInvalidVehicleId() throws Exception {
        mockMvc.perform(patch("/api/vehicles/not-a-uuid/mileage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mileage\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private org.springframework.test.web.servlet.ResultActions updateMileage(UUID id, String mileage) throws Exception {
        return mockMvc.perform(patch("/api/vehicles/{id}/mileage", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mileage\":" + mileage + "}"));
    }

    private Vehicle persistVehicle(boolean active, Long mileage) {
        Customer customer = Customer.create("Cliente Mileage", new TaxId(nextValidCpf()),
                new ContactInfo("mileage@example.test", "+5511999999999"));
        customerRepository.save(customer);

        Vehicle vehicle = Vehicle.reconstitute(
                UUID.randomUUID(),
                customer.id(),
                new LicensePlate(nextPlate()),
                null,
                "Volkswagen",
                "Gol",
                VehicleYear.create(2026, 2026),
                "Prata",
                mileage == null ? null : new Mileage(mileage),
                active);
        vehicleRepository.save(vehicle);
        return vehicle;
    }

    private static String nextPlate() {
        return "KMT%04d".formatted(PLATE_SEQUENCE.getAndIncrement());
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
