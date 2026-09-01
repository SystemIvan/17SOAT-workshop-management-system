package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder;

import br.com.fiap.workshop_management_system.registration.vehicle.application.api.VehicleAvailabilityApi;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.VehicleEligibilityPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ApplicationModuleTest(ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
class VehicleAvailabilityApiApplicationModuleTest {

    @Autowired
    private VehicleAvailabilityApi availabilityApi;

    @Autowired
    private VehicleEligibilityPort eligibilityPort;

    @Test
    void resolvesTheNamedInterfaceAndConsumerAdapterAcrossModules() {
        assertNotNull(availabilityApi);
        assertNotNull(eligibilityPort);
    }
}
