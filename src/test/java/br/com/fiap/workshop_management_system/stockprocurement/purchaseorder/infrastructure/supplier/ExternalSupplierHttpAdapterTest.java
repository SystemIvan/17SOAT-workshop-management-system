package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.infrastructure.supplier;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception.ExternalSupplierInvalidResponseException;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception.ExternalSupplierUnavailableException;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.port.ExternalPurchaseOrderCommand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.port.ExternalPurchaseOrderLine;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.port.ExternalPurchaseOrderResult;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExternalSupplierHttpAdapterTest {

    private WireMockServer wireMock;
    private ExternalSupplierHttpAdapter adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(0);
        wireMock.start();
        ExternalSupplierProperties properties = new ExternalSupplierProperties(
                URI.create(wireMock.baseUrl()), Duration.ofSeconds(1), Duration.ofMillis(200), null);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        adapter = new ExternalSupplierHttpAdapter(
                RestClient.builder().baseUrl(wireMock.baseUrl()).requestFactory(requestFactory).build(),
                properties,
                JsonMapper.builder().build());
    }

    @AfterEach
    void tearDown() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @Test
    void translatesAcceptanceAndSendsOnlyTheSupplierContract() {
        UUID idempotencyKey = UUID.randomUUID();
        wireMock.stubFor(post(urlEqualTo("/api/v1/purchase-orders"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"supplierOrderReference\":\"SUP-123\",\"status\":\"ACCEPTED\"}")));

        ExternalPurchaseOrderResult result = adapter.submit(command(idempotencyKey));

        ExternalPurchaseOrderResult.Accepted accepted =
                assertInstanceOf(ExternalPurchaseOrderResult.Accepted.class, result);
        assertEquals("SUP-123", accepted.externalReference());
        wireMock.verify(postRequestedFor(urlEqualTo("/api/v1/purchase-orders"))
                .withHeader("Idempotency-Key", equalTo(idempotencyKey.toString()))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath(
                        "$.items[0].productCode", equalTo("OIL-FILTER-001"))));
    }

    @Test
    void sanitizesRejectionsAndClassifiesTechnicalFailures() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/purchase-orders"))
                .willReturn(aResponse().withStatus(422).withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"unsafe code!\",\"message\":\"internal detail\"}")));
        ExternalPurchaseOrderResult.Rejected rejected = assertInstanceOf(
                ExternalPurchaseOrderResult.Rejected.class,
                adapter.submit(command(UUID.randomUUID())));
        assertEquals("SUPPLIER_REJECTED", rejected.rejectionCode());

        wireMock.resetAll();
        wireMock.stubFor(post(urlEqualTo("/api/v1/purchase-orders"))
                .willReturn(aResponse().withStatus(503).withBody("secret")));
        assertThrows(ExternalSupplierUnavailableException.class,
                () -> adapter.submit(command(UUID.randomUUID())));

        wireMock.resetAll();
        wireMock.stubFor(post(urlEqualTo("/api/v1/purchase-orders"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("not-json")));
        assertThrows(ExternalSupplierInvalidResponseException.class,
                () -> adapter.submit(command(UUID.randomUUID())));
    }

    @Test
    void classifiesTimeoutWhileReadingAnAcceptedBodyAsUnavailable() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/purchase-orders"))
                .willReturn(aResponse().withStatus(201).withFixedDelay(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"supplierOrderReference\":\"SUP-123\",\"status\":\"ACCEPTED\"}")));

        assertThrows(ExternalSupplierUnavailableException.class,
                () -> adapter.submit(command(UUID.randomUUID())));
    }

    private ExternalPurchaseOrderCommand command(UUID idempotencyKey) {
        return new ExternalPurchaseOrderCommand(
                UUID.randomUUID(),
                idempotencyKey,
                List.of(new ExternalPurchaseOrderLine("OIL-FILTER-001", 3)));
    }
}
