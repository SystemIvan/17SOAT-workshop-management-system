package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.listener
        .RestockedStockReservationRetryListener;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ApplicationModuleTest(ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
class StockRestockingEventsApplicationModuleTest {

    @Autowired
    private RestockedStockReservationRetryListener listener;

    @Test
    void resolvesTheStockRestockingNamedInterfaceAcrossModules() {
        assertNotNull(listener);
    }
}
