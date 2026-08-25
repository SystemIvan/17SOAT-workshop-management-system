package br.com.fiap.workshop_management_system.registration.vehicle.infrastructure.web;

import br.com.fiap.workshop_management_system.registration.customer.domain.model.ContactInfo;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import br.com.fiap.workshop_management_system.registration.vehicle.infrastructure.persistence.VehicleJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Year;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class VehicleControllerTest {

    private static final AtomicInteger CPF_SEQUENCE = new AtomicInteger(600_000_000);
    private static final AtomicInteger PLATE_SEQUENCE = new AtomicInteger(1000);
    private static final AtomicInteger CHASSIS_SEQUENCE = new AtomicInteger(1);

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private VehicleJpaRepository vehicleRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void createsVehicleWithCanonicalIdentityAndOwnId() throws Exception {
        Customer customer = persistCustomer(false);
        String chassis = nextChassis();

        MvcResult result = mockMvc.perform(post("/api/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(customer.id().toString(), "abc-1234", chassis)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/vehicles/")))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.vehicleId").doesNotExist())
                .andExpect(jsonPath("$.customerId").value(customer.id().toString()))
                .andExpect(jsonPath("$.licensePlate").value("ABC1234"))
                .andExpect(jsonPath("$.chassis").value(chassis))
                .andExpect(jsonPath("$.mileage").value(nullValue()))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        String id = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        assertEquals(customer.id(), vehicleRepository.findById(java.util.UUID.fromString(id)).orElseThrow()
                .getCustomerId());
    }

    @Test
    void createsVehicleWithOmittedChassis() throws Exception {
        Customer customer = persistCustomer(false);

        mockMvc.perform(post("/api/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithoutChassis(customer.id().toString(), nextPlate())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chassis").value(nullValue()))
                .andExpect(jsonPath("$.mileage").value(nullValue()));
    }

    @Test
    void createsVehicleWithInitialMileageAndAcceptsExplicitNull() throws Exception {
        Customer customer = persistCustomer(false);

        MvcResult withMileage = mockMvc.perform(post("/api/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithMileage(customer.id().toString(), nextPlate(), "42500")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mileage").value(42_500))
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(withMileage.getResponse().getContentAsString(), "$.id");
        assertEquals(42_500L, vehicleRepository.findById(java.util.UUID.fromString(id)).orElseThrow().getMileage());

        mockMvc.perform(post("/api/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithMileage(customer.id().toString(), nextPlate(), "null")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mileage").value(nullValue()));
    }

    @Test
    void rejectsInvalidInitialMileageRepresentations() throws Exception {
        Customer customer = persistCustomer(false);
        String[] invalidMileageValues = {
                "-1", "1.5", "\"42500\"", "true", "[]", "{}", "9223372036854775808"
        };

        for (String mileage : invalidMileageValues) {
            mockMvc.perform(post("/api/vehicles")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bodyWithMileage(customer.id().toString(), nextPlate(), mileage)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void rejectsInvalidVehicleBeforeCustomerLookup() throws Exception {
        String body = body(java.util.UUID.randomUUID().toString(), "invalid", null);

        mockMvc.perform(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VEHICLE"));

        String invalidYearBody = bodyWithYear(
                java.util.UUID.randomUUID().toString(), nextPlate(), null, Year.now().getValue() + 2);
        mockMvc.perform(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON).content(invalidYearBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VEHICLE"));

        Customer customer = persistCustomer(false);
        String blankChassis = body(customer.id().toString(), nextPlate(), "   ");
        mockMvc.perform(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON).content(blankChassis))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsMissingAndArchivedCustomer() throws Exception {
        mockMvc.perform(post("/api/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(java.util.UUID.randomUUID().toString(), nextPlate(), null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));

        Customer archivedCustomer = persistCustomer(true);
        mockMvc.perform(post("/api/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(archivedCustomer.id().toString(), nextPlate(), null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUSTOMER_ARCHIVED"));
    }

    @Test
    void rejectsDuplicatePlateAndChassis() throws Exception {
        Customer customer = persistCustomer(false);
        String plate = nextPlate();
        String chassis = nextChassis();
        mockMvc.perform(post("/api/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(customer.id().toString(), plate, chassis)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(customer.id().toString(), plate, nextChassis())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VEHICLE_LICENSE_PLATE_ALREADY_EXISTS"));

        mockMvc.perform(post("/api/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(customer.id().toString(), nextPlate(), chassis)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VEHICLE_CHASSIS_ALREADY_EXISTS"));
    }

    private Customer persistCustomer(boolean archived) {
        Customer customer = Customer.create("Cliente Vehicle", new TaxId(nextValidCpf()),
                new ContactInfo("vehicle@example.test", "+5511999999999"));
        if (archived) {
            customer.archive();
        }
        customerRepository.save(customer);
        return customer;
    }

    private static String body(String customerId, String licensePlate, String chassis) {
        return bodyWithYear(customerId, licensePlate, chassis, Year.now().getValue());
    }

    private static String bodyWithYear(String customerId, String licensePlate, String chassis, int year) {
        String chassisJson = chassis == null ? "null" : "\"" + chassis + "\"";
        return """
                {
                  "customerId": "%s",
                  "licensePlate": "%s",
                  "chassis": %s,
                  "brand": "Volkswagen",
                  "model": "Gol",
                  "year": %d,
                  "color": "Prata"
                }
                """.formatted(customerId, licensePlate, chassisJson, year);
    }

    private static String bodyWithoutChassis(String customerId, String licensePlate) {
        return """
                {
                  "customerId": "%s",
                  "licensePlate": "%s",
                  "brand": "Volkswagen",
                  "model": "Gol",
                  "year": %d,
                  "color": "Prata"
                }
                """.formatted(customerId, licensePlate, Year.now().getValue());
    }

    private static String bodyWithMileage(String customerId, String licensePlate, String mileageJson) {
        return """
                {
                  "customerId": "%s",
                  "licensePlate": "%s",
                  "brand": "Volkswagen",
                  "model": "Gol",
                  "year": %d,
                  "color": "Prata",
                  "mileage": %s
                }
                """.formatted(customerId, licensePlate, Year.now().getValue(), mileageJson);
    }

    private static String nextPlate() {
        return "TST%04d".formatted(PLATE_SEQUENCE.getAndIncrement());
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
