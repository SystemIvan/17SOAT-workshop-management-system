package br.com.fiap.workshop_management_system.registration.vehicle.infrastructure.web;

import br.com.fiap.workshop_management_system.registration.customer.domain.model.ContactInfo;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.ChassisNumber;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.LicensePlate;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Vehicle;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.VehicleYear;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.repository.VehicleRepository;
import br.com.fiap.workshop_management_system.registration.vehicle.infrastructure.persistence.VehicleJpaEntity;
import br.com.fiap.workshop_management_system.registration.vehicle.infrastructure.persistence.VehicleJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Year;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class UpdateVehicleControllerTest {

    private static final AtomicInteger CPF_SEQUENCE = new AtomicInteger(400_000_000);
    private static final AtomicInteger PLATE_SEQUENCE = new AtomicInteger(2000);
    private static final AtomicInteger CHASSIS_SEQUENCE = new AtomicInteger(300);

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
    void updatesDescriptionsAndAddsCanonicalChassis() throws Exception {
        Vehicle vehicle = persistVehicle(true, null);
        String chassis = nextChassis();

        mockMvc.perform(patch("/api/vehicles/{id}", vehicle.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("\" " + chassis.toLowerCase() + " \"", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(vehicle.id().toString()))
                .andExpect(jsonPath("$.customerId").value(vehicle.customerId().toString()))
                .andExpect(jsonPath("$.licensePlate").value(vehicle.licensePlate().value()))
                .andExpect(jsonPath("$.brand").value("Fiat"))
                .andExpect(jsonPath("$.model").value("Argo"))
                .andExpect(jsonPath("$.year").value(Year.now().getValue()))
                .andExpect(jsonPath("$.color").value("Branco"))
                .andExpect(jsonPath("$.chassis").value(chassis))
                .andExpect(jsonPath("$.active").value(true));

        VehicleJpaEntity stored = jpaRepository.findById(vehicle.id()).orElseThrow();
        assertEquals(chassis, stored.getChassisNumber());
        assertEquals("Fiat", stored.getBrand());
    }

    @Test
    void preservesChassisForOmittedNullEmptyAndBlankValues() throws Exception {
        String chassis = nextChassis();
        Vehicle vehicle = persistVehicle(true, chassis);
        String[] bodies = {
                updateBody(null, ""),
                updateBody("null", ""),
                updateBody("\"\"", ""),
                updateBody("\"   \"", "")
        };

        for (String body : bodies) {
            mockMvc.perform(patch("/api/vehicles/{id}", vehicle.id())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.chassis").value(chassis));
        }

        assertEquals(chassis, jpaRepository.findById(vehicle.id()).orElseThrow().getChassisNumber());
    }

    @Test
    void replacesAvailableChassisAndRejectsChassisFromAnotherVehicle() throws Exception {
        String originalChassis = nextChassis();
        String occupiedChassis = nextChassis();
        String replacementChassis = nextChassis();
        Vehicle target = persistVehicle(true, originalChassis);
        persistVehicle(true, occupiedChassis);

        mockMvc.perform(patch("/api/vehicles/{id}", target.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("\"" + occupiedChassis + "\"", "")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VEHICLE_CHASSIS_ALREADY_EXISTS"));
        assertEquals(originalChassis, jpaRepository.findById(target.id()).orElseThrow().getChassisNumber());

        mockMvc.perform(patch("/api/vehicles/{id}", target.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("\"" + replacementChassis + "\"", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chassis").value(replacementChassis));
    }

    @Test
    void rejectsMissingAndArchivedVehicle() throws Exception {
        mockMvc.perform(patch("/api/vehicles/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(null, "")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_FOUND"));

        Vehicle archived = persistVehicle(false, nextChassis());
        mockMvc.perform(patch("/api/vehicles/{id}", archived.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("\"" + nextChassis() + "\"", "")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VEHICLE_ARCHIVED"));
    }

    @Test
    void rejectsInvalidDescriptionsYearChassisAndId() throws Exception {
        Vehicle vehicle = persistVehicle(true, null);

        mockMvc.perform(patch("/api/vehicles/{id}", vehicle.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"model":"Argo","year":2026,"color":"Branco"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(patch("/api/vehicles/{id}", vehicle.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(null, "").replace(
                                "\"year\": " + Year.now().getValue(),
                                "\"year\": " + (Year.now().getValue() + 2))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VEHICLE"));

        mockMvc.perform(patch("/api/vehicles/{id}", vehicle.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("\"invalid\"", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VEHICLE"));

        mockMvc.perform(patch("/api/vehicles/not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(null, "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void ignoresForbiddenFieldsWithoutChangingIdentityOrLifecycle() throws Exception {
        Vehicle vehicle = persistVehicle(true, null);
        UUID differentCustomerId = UUID.randomUUID();
        String extraFields = """
                ,"customerId":"%s","licensePlate":"ZZZ9999","active":false
                """.formatted(differentCustomerId);

        mockMvc.perform(patch("/api/vehicles/{id}", vehicle.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(null, extraFields)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(vehicle.customerId().toString()))
                .andExpect(jsonPath("$.licensePlate").value(vehicle.licensePlate().value()))
                .andExpect(jsonPath("$.active").value(true));
    }

    private Vehicle persistVehicle(boolean active, String chassis) {
        Customer customer = Customer.create("Cliente Vehicle", new TaxId(nextValidCpf()),
                new ContactInfo("vehicle@example.test", "+5511999999999"));
        customerRepository.save(customer);

        ChassisNumber chassisNumber = chassis == null ? null : new ChassisNumber(chassis);
        Vehicle vehicle = Vehicle.reconstitute(UUID.randomUUID(), customer.id(), new LicensePlate(nextPlate()),
                chassisNumber, "Volkswagen", "Gol", VehicleYear.create(2026, 2026), "Prata", null, active);
        vehicleRepository.save(vehicle);
        return vehicle;
    }

    private static String updateBody(String chassisJson, String extraFields) {
        String chassisProperty = chassisJson == null ? "" : ",\"chassis\":" + chassisJson;
        return """
                {
                  "brand": " Fiat ",
                  "model": " Argo ",
                  "year": %d,
                  "color": " Branco "%s%s
                }
                """.formatted(Year.now().getValue(), chassisProperty, extraFields);
    }

    private static String nextPlate() {
        return "UPD%04d".formatted(PLATE_SEQUENCE.getAndIncrement());
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
