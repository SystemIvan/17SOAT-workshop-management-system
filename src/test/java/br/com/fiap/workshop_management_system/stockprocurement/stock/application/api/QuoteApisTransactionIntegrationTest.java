package br.com.fiap.workshop_management_system.stockprocurement.stock.application.api;

import br.com.fiap.workshop_management_system.registration.servicecatalog.application.api.CatalogServiceQuoteApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class QuoteApisTransactionIntegrationTest {

    @Autowired
    private CatalogServiceQuoteApi catalogServiceQuoteApi;

    @Autowired
    private StockItemQuoteApi stockItemQuoteApi;

    @Test
    void requireAConsumerTransaction() {
        assertThrows(IllegalTransactionStateException.class,
                () -> catalogServiceQuoteApi.quote(UUID.randomUUID()));
        assertThrows(IllegalTransactionStateException.class,
                () -> stockItemQuoteApi.quote(List.of(UUID.randomUUID())));
    }
}
