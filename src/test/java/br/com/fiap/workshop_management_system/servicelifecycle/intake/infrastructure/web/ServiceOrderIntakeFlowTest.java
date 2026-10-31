package br.com.fiap.workshop_management_system.servicelifecycle.intake.infrastructure.web;

import br.com.fiap.workshop_management_system.identity.auth.application.port.TokenIssuer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.ContactInfo;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogService;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogServiceName;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CurrencyCode;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.Money;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.repository.CatalogServiceRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.notification.application.port
        .CustomerEstimateNotificationPort;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.model.Specialty;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.model.Technician;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.repository.TechnicianRepository;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Price;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Quantity;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Sku;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItemType;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;
import br.com.fiap.workshop_management_system.testsupport.TestAuth;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end acceptance criteria of {@code service-order-full-intake} against the test database: registrations,
 * Service Order, Diagnosis and Estimate, prices copied from the registrations, atomic rollback and the after-commit
 * Estimate notification.
 */
@SpringBootTest
class ServiceOrderIntakeFlowTest {

    private static final AtomicInteger CPF_SEQUENCE = new AtomicInteger(970_000_000);
    private static final AtomicInteger PLATE_SEQUENCE = new AtomicInteger(7000);

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TokenIssuer tokenIssuer;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CatalogServiceRepository catalogServiceRepository;

    @Autowired
    private StockItemRepository stockItemRepository;

    @Autowired
    private TechnicianRepository technicianRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private CustomerEstimateNotificationPort customerEstimateNotificationPort;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .defaultRequest(MockMvcRequestBuilders.get("/")
                        .header("Authorization", "Bearer " + TestAuth.adminToken(tokenIssuer)))
                .build();
    }

    @Test
    void opensWithNewRegistrationsDiagnosisAndEstimateUsingRegisteredPrices() throws Exception {
        String document = nextValidCpf();
        String plate = nextPlate();
        UUID catalogServiceId = catalogService("150.00", true);
        UUID stockItemId = stockItem("45.90", 10, true);
        UUID technicianId = technician();

        MvcResult result = intake(fullBody(document, plate, technicianId, catalogServiceId, stockItemId, 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customer.created").value(true))
                .andExpect(jsonPath("$.vehicle.created").value(true))
                .andExpect(jsonPath("$.estimateId").isNotEmpty())
                .andExpect(jsonPath("$.serviceOrder.statusSnapshot").value("AWAITING_APPROVAL"))
                .andExpect(jsonPath("$.serviceOrder.vehicleSnapshot.licensePlate").value(plate))
                .andExpect(jsonPath("$.serviceOrder.vehicleSnapshot.brand").value("Fiat"))
                .andExpect(jsonPath("$.serviceOrder.vehicleSnapshot.year").value(2022))
                .andExpect(jsonPath("$.serviceOrder.diagnosisAssigneeId").value(technicianId.toString()))
                .andExpect(jsonPath("$.serviceOrder.executions", hasSize(1)))
                .andExpect(jsonPath("$.serviceOrder.executions[0].catalogServiceId")
                        .value(catalogServiceId.toString()))
                .andExpect(jsonPath("$.serviceOrder.executions[0].diagnosedByTechnicianId")
                        .value(technicianId.toString()))
                // The body sends 0.01 for both prices; the registered values must win.
                .andExpect(jsonPath("$.serviceOrder.executions[0].price.value").value(150.00))
                .andExpect(jsonPath("$.serviceOrder.executions[0].stockRequirements[0].priceSnapshot.value")
                        .value(45.90))
                .andExpect(jsonPath("$.serviceOrder.executions[0].stockRequirements[0].type").value("PART"))
                .andExpect(jsonPath("$.serviceOrder.executions[0].stockRequirements[0].quantity").value(2))
                .andReturn();

        String estimateId = JsonPath.read(result.getResponse().getContentAsString(), "$.estimateId");
        String serviceOrderId = JsonPath.read(result.getResponse().getContentAsString(), "$.serviceOrder.id");
        mockMvc.perform(get("/api/estimates/" + estimateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total.value").value(241.80));
        mockMvc.perform(get("/api/service-orders/" + serviceOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusSnapshot").value("AWAITING_APPROVAL"));
        verify(customerEstimateNotificationPort, timeout(5_000))
                .notifyEstimateGenerated(eq(UUID.fromString(estimateId)), eq(UUID.fromString(serviceOrderId)),
                        any(), any());
    }

    @Test
    void reusesExistingRegistrationsWithoutChangingThem() throws Exception {
        String document = nextValidCpf();
        String plate = nextPlate();
        intake(noServicesBody(document, plate, "Nome Original", "original@example.test", "Fiat"))
                .andExpect(status().isCreated());

        MvcResult result = intake(noServicesBody(document, plate, "Outro Nome", "outro@example.test", "Outra"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customer.created").value(false))
                .andExpect(jsonPath("$.vehicle.created").value(false))
                .andExpect(jsonPath("$.serviceOrder.vehicleSnapshot.brand").value("Fiat"))
                .andReturn();

        String customerId = JsonPath.read(result.getResponse().getContentAsString(), "$.customer.id");
        mockMvc.perform(get("/api/customers/" + customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nome Original"))
                .andExpect(jsonPath("$.contactInfo.email").value("original@example.test"));
    }

    @Test
    void registersNewVehicleForAnExistingCustomer() throws Exception {
        String document = nextValidCpf();
        Customer existing = Customer.create("Cliente Existente", new TaxId(document),
                new ContactInfo("existente@example.test", "+5511999999999"));
        customerRepository.save(existing);

        intake(noServicesBody(document, nextPlate(), null, null, "Fiat"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customer.id").value(existing.id().toString()))
                .andExpect(jsonPath("$.customer.created").value(false))
                .andExpect(jsonPath("$.vehicle.created").value(true));
    }

    @Test
    void opensWithoutServicesInReceivedStatus() throws Exception {
        intake(noServicesBody(nextValidCpf(), nextPlate(), "Cliente", "cliente@example.test", "Fiat"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceOrder.statusSnapshot").value("RECEIVED"))
                .andExpect(jsonPath("$.serviceOrder.executions", hasSize(0)))
                .andExpect(jsonPath("$.estimateId").isEmpty());
    }

    @Test
    void rejectsPlateOwnedByAnotherCustomer() throws Exception {
        String plate = nextPlate();
        intake(noServicesBody(nextValidCpf(), plate, "Dono", "dono@example.test", "Fiat"))
                .andExpect(status().isCreated());

        intake(noServicesBody(nextValidCpf(), plate, "Outro", "outro@example.test", "Fiat"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VEHICLE_OWNED_BY_ANOTHER_CUSTOMER"));
    }

    @Test
    void rejectsUnknownTechnicianAndStockItemAsNotFound() throws Exception {
        UUID catalogServiceId = catalogService("150.00", true);

        intake(fullBody(nextValidCpf(), nextPlate(), UUID.randomUUID(), catalogServiceId, stockItem("10.00", 5, true),
                1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        intake(fullBody(nextValidCpf(), nextPlate(), technician(), catalogServiceId, UUID.randomUUID(), 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STOCK_ITEM_NOT_FOUND"));
    }

    @Test
    void rejectsInactiveStockItemAsConflict() throws Exception {
        intake(fullBody(nextValidCpf(), nextPlate(), technician(), catalogService("150.00", true),
                stockItem("10.00", 5, false), 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_ITEM_INACTIVE"));
    }

    @Test
    void rollsBackNewRegistrationsWhenALaterStepFails() throws Exception {
        String document = nextValidCpf();
        String plate = nextPlate();
        UUID archivedService = catalogService("150.00", false);
        UUID stockItemId = stockItem("45.90", 10, true);
        UUID technicianId = technician();
        long customers = count("customers");
        long vehicles = count("vehicles");
        long serviceOrders = count("service_orders");
        long estimates = count("estimates");

        intake(fullBody(document, plate, technicianId, archivedService, stockItemId, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_SERVICE_ARCHIVED"));

        assertEquals(customers, count("customers"));
        assertEquals(vehicles, count("vehicles"));
        assertEquals(serviceOrders, count("service_orders"));
        assertEquals(estimates, count("estimates"));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "select count(*) from vehicles where license_plate = ?", Long.class, plate));
        verify(customerEstimateNotificationPort, after(1_000).never())
                .notifyEstimateGenerated(any(), any(), any(), any());
    }

    @Test
    void recordsStockShortageWithoutRejectingTheIntake() throws Exception {
        UUID stockItemId = stockItem("45.90", 1, true);

        intake(fullBody(nextValidCpf(), nextPlate(), technician(), catalogService("150.00", true), stockItemId, 3))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceOrder.executions[0].stockAvailability[0].stockItemId")
                        .value(stockItemId.toString()))
                .andExpect(jsonPath("$.serviceOrder.executions[0].stockAvailability[0].status")
                        .value("INSUFFICIENT_QUANTITY"))
                .andExpect(jsonPath("$.serviceOrder.executions[0].stockAvailability[0].shortageQuantity")
                        .value(2));
    }

    private ResultActions intake(String body) throws Exception {
        return mockMvc.perform(post("/api/service-orders/intake")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
    }

    private UUID catalogService(String price, boolean active) {
        CatalogService service = CatalogService.create(
                new CatalogServiceName("Serviço intake " + UUID.randomUUID()),
                new Money(new BigDecimal(price), CurrencyCode.BRL));
        if (!active) {
            service.archive();
        }
        catalogServiceRepository.save(service);
        return service.id();
    }

    private UUID stockItem(String price, int available, boolean active) {
        StockItem item = StockItem.create(new Sku("INT-" + UUID.randomUUID()), "Filtro de óleo", StockItemType.PART,
                new Price(new BigDecimal(price), br.com.fiap.workshop_management_system.stockprocurement.stock
                        .domain.model.CurrencyCode.BRL),
                new Quantity(available));
        if (!active) {
            item.deactivate();
        }
        stockItemRepository.save(item);
        return item.id();
    }

    private UUID technician() {
        Technician technician = Technician.create("Técnico Intake", Set.of(Specialty.MECHANICAL));
        technicianRepository.save(technician);
        return technician.id();
    }

    private static String fullBody(String document, String plate, UUID technicianId, UUID catalogServiceId,
            UUID stockItemId, int quantity) {
        return """
                {
                  "customer": {
                    "document": "%s",
                    "name": "Cliente Intake",
                    "contactInfo": { "email": "intake@example.test", "phone": "+5511999999999" }
                  },
                  "vehicle": { "licensePlate": "%s", "brand": "Fiat", "model": "Argo", "year": 2022,
                               "color": "Prata" },
                  "priority": "HIGH",
                  "initialAssessment": "Barulho na suspensão dianteira",
                  "diagnosisTechnicianId": "%s",
                  "services": [
                    {
                      "catalogServiceId": "%s",
                      "price": { "value": 0.01, "currency": "BRL" },
                      "parts": [ { "stockItemId": "%s", "quantity": %d, "price": 0.01 } ]
                    }
                  ]
                }
                """.formatted(document, plate, technicianId, catalogServiceId, stockItemId, quantity);
    }

    private static String noServicesBody(String document, String plate, String name, String email, String brand) {
        String contact = email == null ? "null"
                : "{ \"email\": \"%s\", \"phone\": \"+5511999999999\" }".formatted(email);
        String customerName = name == null ? "null" : "\"%s\"".formatted(name);
        return """
                {
                  "customer": { "document": "%s", "name": %s, "contactInfo": %s },
                  "vehicle": { "licensePlate": "%s", "brand": "%s", "model": "Argo", "year": 2022,
                               "color": "Prata" },
                  "initialAssessment": "Revisão preventiva"
                }
                """.formatted(document, customerName, contact, plate, brand);
    }

    private static String nextPlate() {
        return "FLW%04d".formatted(PLATE_SEQUENCE.getAndIncrement());
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
