package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.infrastructure.web;

import br.com.fiap.workshop_management_system.identity.auth.application.port.TokenIssuer;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception.ExternalSupplierUnavailableException;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.port.ExternalPurchaseOrderCommand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.port.ExternalPurchaseOrderResult;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.port.ExternalSupplierGateway;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReserveStockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReserveStockItemsCommand;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.StockReservationApi;
import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.application.event.StockItemsRestockedEvent;
import br.com.fiap.workshop_management_system.testsupport.TestAuth;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(PurchaseOrderFlowIntegrationTest.SupplierTestConfiguration.class)
class PurchaseOrderFlowIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private StockReservationApi stockReservationApi;

    @Autowired
    private ControllableSupplierGateway supplierGateway;

    @Autowired
    private TokenIssuer tokenIssuer;

    @Autowired
    private RestockEventProbe restockEventProbe;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .defaultRequest(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/").header("Authorization", "Bearer " + TestAuth.adminToken(tokenIssuer)))
                .build();
        supplierGateway.reset();
        restockEventProbe.clear();
    }

    @Test
    void createsARepairDemandAndConfirmsAnIdempotentDemandBackedOrder() throws Exception {
        UUID stockItemId = createStockItem("PO-DEMAND-", 1);
        UUID serviceExecutionId = UUID.randomUUID();
        stockReservationApi.reserveAll(List.of(new ReserveStockItemsCommand(
                serviceExecutionId,
                List.of(new ReserveStockItem(stockItemId, 4)))));

        MvcResult demandResult = mockMvc.perform(get("/api/purchase-demands")
                        .param("origin", "PENDING_REPAIR")
                        .param("stockItemId", stockItemId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].suggestedQuantity").value(3))
                .andExpect(jsonPath("$[0].serviceExecutionId").value(serviceExecutionId.toString()))
                .andReturn();
        String demandId = JsonPath.read(demandResult.getResponse().getContentAsString(), "$[0].id");
        UUID idempotencyKey = UUID.randomUUID();
        String body = orderBody(stockItemId, 3, demandId);

        MvcResult creation = mockMvc.perform(post("/api/purchase-orders")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/purchase-orders/")))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.externalReference").value("SUP-" + idempotencyKey))
                .andExpect(jsonPath("$.demandIds[0]").value(demandId))
                .andReturn();
        String purchaseOrderId = JsonPath.read(creation.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/purchase-orders")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(purchaseOrderId));
        org.junit.jupiter.api.Assertions.assertEquals(1, supplierGateway.submissions());

        mockMvc.perform(get("/api/purchase-orders/{id}", purchaseOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
        mockMvc.perform(get("/api/purchase-demands").param("stockItemId", stockItemId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void createsAnAdHocOrderAndRecoversAPendingSubmissionWithTheSameKey() throws Exception {
        UUID stockItemId = createStockItem("PO-ADHOC-", 10);
        UUID idempotencyKey = UUID.randomUUID();
        String body = orderBody(stockItemId, 2, null);
        supplierGateway.unavailable();

        mockMvc.perform(post("/api/purchase-orders")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EXTERNAL_SUPPLIER_UNAVAILABLE"));

        supplierGateway.accept();
        mockMvc.perform(post("/api/purchase-orders")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.demandIds", hasSize(0)));
        org.junit.jupiter.api.Assertions.assertEquals(2, supplierGateway.submissions());
    }

    @Test
    void rejectsInvalidContractsAndChangedIdempotentPayload() throws Exception {
        UUID stockItemId = createStockItem("PO-CONFLICT-", 10);
        UUID idempotencyKey = UUID.randomUUID();

        mockMvc.perform(post("/api/purchase-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(stockItemId, 1, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/purchase-orders")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(stockItemId, 1, null)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/purchase-orders")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(stockItemId, 2, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PURCHASE_ORDER_IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void closesAnOpenOrderIdempotentlyAndListsBothConfirmedStatuses() throws Exception {
        UUID stockItemId = createStockItem("PO-CLOSE-", 7);
        MvcResult creation = mockMvc.perform(post("/api/purchase-orders")
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(stockItemId, 3, null)))
                .andExpect(status().isCreated())
                .andReturn();
        String purchaseOrderId = JsonPath.read(creation.getResponse().getContentAsString(), "$.id");

        MvcResult close = mockMvc.perform(post("/api/purchase-orders/{id}/close", purchaseOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedAt", notNullValue()))
                .andExpect(jsonPath("$.closedByUserAccountId", notNullValue()))
                .andReturn();
        String closedAt = JsonPath.read(close.getResponse().getContentAsString(), "$.closedAt");
        String closedBy = JsonPath.read(close.getResponse().getContentAsString(), "$.closedByUserAccountId");

        mockMvc.perform(post("/api/purchase-orders/{id}/close", purchaseOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closedAt").value(closedAt))
                .andExpect(jsonPath("$.closedByUserAccountId").value(closedBy));
        mockMvc.perform(get("/api/purchase-orders/{id}", purchaseOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        mockMvc.perform(get("/api/purchase-orders").param("status", "CLOSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(purchaseOrderId));
        mockMvc.perform(get("/api/purchase-orders").param("receiptStatus", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(purchaseOrderId));
    }

    @Test
    void receivesAClosedOrderExactlyOnceAndKeepsTheImmutableSnapshots() throws Exception {
        UUID stockItemId = createStockItem("PO-RECEIPT-", 1);
        MvcResult creation = mockMvc.perform(post("/api/purchase-orders")
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(stockItemId, 3, null)))
                .andExpect(status().isCreated())
                .andReturn();
        String purchaseOrderId = JsonPath.read(creation.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(post("/api/purchase-orders/{id}/close", purchaseOrderId))
                .andExpect(status().isOk());

        MvcResult receipt = mockMvc.perform(post("/api/purchase-orders/{id}/receipt", purchaseOrderId))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/purchase-orders/" + purchaseOrderId + "/receipt"))
                .andExpect(jsonPath("$.purchaseOrderId").value(purchaseOrderId))
                .andExpect(jsonPath("$.lines[0].stockItemId").value(stockItemId.toString()))
                .andExpect(jsonPath("$.lines[0].quantity").value(3))
                .andExpect(jsonPath("$.lines[0].availableBefore").value(1))
                .andExpect(jsonPath("$.lines[0].availableAfter").value(4))
                .andReturn();
        String receiptId = JsonPath.read(receipt.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/purchase-orders/{id}/receipt", purchaseOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(receiptId))
                .andExpect(jsonPath("$.lines[0].availableAfter").value(4));
        org.junit.jupiter.api.Assertions.assertEquals(2, restockEventProbe.events().size());
        org.junit.jupiter.api.Assertions.assertEquals(receiptId, restockEventProbe.events().getFirst().stockReceiptId()
                .toString());
        org.junit.jupiter.api.Assertions.assertEquals(stockItemId, restockEventProbe.events().getFirst()
                .stockItemIds().getFirst());
        mockMvc.perform(get("/api/purchase-orders/{id}/receipt", purchaseOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(receiptId));
        mockMvc.perform(get("/api/purchase-orders").param("receiptStatus", "RECEIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(purchaseOrderId))
                .andExpect(jsonPath("$[0].receiptId").value(receiptId));
        mockMvc.perform(get("/api/stock-items/{id}", stockItemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(4));
    }

    @Test
    void rejectsReceiptForAnOpenPurchaseOrder() throws Exception {
        UUID stockItemId = createStockItem("PO-OPEN-RECEIPT-", 0);
        MvcResult creation = mockMvc.perform(post("/api/purchase-orders")
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(stockItemId, 1, null)))
                .andExpect(status().isCreated())
                .andReturn();
        String purchaseOrderId = JsonPath.read(creation.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/purchase-orders/{id}/receipt", purchaseOrderId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PURCHASE_ORDER_NOT_CLOSED"));
    }

    private UUID createStockItem(String prefix, int availableQuantity) throws Exception {
        String sku = prefix + System.nanoTime();
        String body = "{\"sku\":\"%s\",\"name\":\"Purchase item\",\"type\":\"PART\","
                + "\"price\":{\"value\":10.00,\"currency\":\"BRL\"},\"availableQuantity\":%d}";
        MvcResult result = mockMvc.perform(post("/api/stock-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(sku, availableQuantity)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private String orderBody(UUID stockItemId, int quantity, String demandId) {
        String demands = demandId == null ? "[]" : "[\"" + demandId + "\"]";
        return "{\"demandIds\":" + demands + ",\"lines\":[{\"stockItemId\":\"" + stockItemId
                + "\",\"quantity\":" + quantity + "}]}";
    }

    @TestConfiguration
    static class SupplierTestConfiguration {

        @Bean
        @Primary
        ControllableSupplierGateway controllableSupplierGateway() {
            return new ControllableSupplierGateway();
        }

        @Bean
        RestockEventProbe restockEventProbe() {
            return new RestockEventProbe();
        }
    }

    static class RestockEventProbe {

        private final List<StockItemsRestockedEvent> events = new CopyOnWriteArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void on(StockItemsRestockedEvent event) {
            events.add(event);
        }

        List<StockItemsRestockedEvent> events() {
            return List.copyOf(events);
        }

        void clear() {
            events.clear();
        }
    }

    static class ControllableSupplierGateway implements ExternalSupplierGateway {

        private final AtomicInteger submissions = new AtomicInteger();
        private volatile boolean unavailable;

        @Override
        public ExternalPurchaseOrderResult submit(ExternalPurchaseOrderCommand command) {
            submissions.incrementAndGet();
            if (unavailable) {
                throw new ExternalSupplierUnavailableException("Test supplier is unavailable");
            }
            return new ExternalPurchaseOrderResult.Accepted("SUP-" + command.idempotencyKey());
        }

        void accept() {
            unavailable = false;
        }

        void reset() {
            accept();
            submissions.set(0);
        }

        void unavailable() {
            unavailable = true;
        }

        int submissions() {
            return submissions.get();
        }
    }
}
