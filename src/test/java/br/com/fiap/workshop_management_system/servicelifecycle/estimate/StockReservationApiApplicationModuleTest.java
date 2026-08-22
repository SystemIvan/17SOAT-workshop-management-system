package br.com.fiap.workshop_management_system.servicelifecycle.estimate;

import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.StockReservationApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ApplicationModuleTest(ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
class StockReservationApiApplicationModuleTest {

    @Autowired
    private StockReservationApi stockReservationApi;

    @Test
    void resolvesTheStockReservationNamedInterfaceAcrossModules() {
        assertNotNull(stockReservationApi);
    }
}
