package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.infrastructure.supplier;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception.ExternalSupplierInvalidResponseException;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception.ExternalSupplierUnavailableException;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.port.ExternalPurchaseOrderCommand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.port.ExternalPurchaseOrderResult;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.port.ExternalSupplierGateway;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class ExternalSupplierHttpAdapter implements ExternalSupplierGateway {

    private static final Pattern SAFE_REJECTION_CODE = Pattern.compile("[A-Z0-9_]{1,64}");
    private static final Pattern SAFE_EXTERNAL_REFERENCE = Pattern.compile("[A-Za-z0-9._:-]{1,255}");
    private static final String GENERIC_REJECTION_CODE = "SUPPLIER_REJECTED";

    private final RestClient restClient;
    private final ExternalSupplierProperties properties;
    private final ObjectMapper objectMapper;

    public ExternalSupplierHttpAdapter(
            RestClient externalSupplierRestClient,
            ExternalSupplierProperties properties,
            ObjectMapper objectMapper) {
        this.restClient = externalSupplierRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public ExternalPurchaseOrderResult submit(ExternalPurchaseOrderCommand command) {
        SupplierPurchaseOrderRequest request = new SupplierPurchaseOrderRequest(
                command.purchaseOrderId().toString(),
                command.lines().stream()
                        .map(line -> new SupplierPurchaseOrderItem(line.productCode(), line.quantity()))
                        .toList());
        try {
            return restClient.post()
                    .uri("/api/v1/purchase-orders")
                    .header("Idempotency-Key", command.idempotencyKey().toString())
                    .headers(headers -> addApiKey(headers, properties.apiKey()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange((clientRequest, response) -> translate(response));
        } catch (ExternalSupplierInvalidResponseException | ExternalSupplierUnavailableException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new ExternalSupplierUnavailableException("External supplier is unavailable", exception);
        }
    }

    private ExternalPurchaseOrderResult translate(org.springframework.http.client.ClientHttpResponse response) {
        int status;
        try {
            status = response.getStatusCode().value();
        } catch (IOException exception) {
            if (hasSocketTimeout(exception)) {
                throw new ExternalSupplierUnavailableException("External supplier is unavailable", exception);
            }
            throw new ExternalSupplierInvalidResponseException(
                    "External supplier returned an invalid response", exception);
        }
        if (status == 201) {
            SupplierPurchaseOrderAcceptedBody body = readBody(response, SupplierPurchaseOrderAcceptedBody.class);
            if (body == null || !"ACCEPTED".equals(body.status())
                    || body.supplierOrderReference() == null
                    || !SAFE_EXTERNAL_REFERENCE.matcher(body.supplierOrderReference()).matches()) {
                throw new ExternalSupplierInvalidResponseException("External supplier returned an invalid response");
            }
            return new ExternalPurchaseOrderResult.Accepted(body.supplierOrderReference().trim());
        }
        if (status == 422) {
            SupplierPurchaseOrderRejectedBody body = readBody(response, SupplierPurchaseOrderRejectedBody.class);
            String code = body == null ? null : body.code();
            String safeCode = code != null && SAFE_REJECTION_CODE.matcher(code).matches()
                    ? code
                    : GENERIC_REJECTION_CODE;
            return new ExternalPurchaseOrderResult.Rejected(safeCode);
        }
        if (status >= 500) {
            throw new ExternalSupplierUnavailableException("External supplier is unavailable");
        }
        throw new ExternalSupplierInvalidResponseException("External supplier returned an incompatible response");
    }

    private <T> T readBody(org.springframework.http.client.ClientHttpResponse response, Class<T> bodyType) {
        try {
            return objectMapper.readValue(response.getBody(), bodyType);
        } catch (Exception exception) {
            if (hasSocketTimeout(exception)) {
                throw new ExternalSupplierUnavailableException("External supplier is unavailable", exception);
            }
            throw new ExternalSupplierInvalidResponseException(
                    "External supplier returned an invalid response", exception);
        }
    }

    private boolean hasSocketTimeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            if (current == current.getCause()) {
                return false;
            }
            current = current.getCause();
        }
        return false;
    }

    private void addApiKey(HttpHeaders headers, String apiKey) {
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set("X-API-Key", apiKey);
        }
    }

    private record SupplierPurchaseOrderRequest(
            String workshopOrderReference,
            List<SupplierPurchaseOrderItem> items) {
    }

    private record SupplierPurchaseOrderItem(String productCode, int quantity) {
    }

    private record SupplierPurchaseOrderAcceptedBody(String supplierOrderReference, String status) {
    }

    private record SupplierPurchaseOrderRejectedBody(String code, String message) {
    }
}
