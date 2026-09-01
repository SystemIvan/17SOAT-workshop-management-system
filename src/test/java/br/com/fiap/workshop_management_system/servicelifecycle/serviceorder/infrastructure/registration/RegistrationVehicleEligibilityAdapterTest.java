package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.registration;

import br.com.fiap.workshop_management_system.registration.vehicle.application.api.VehicleAvailability;
import br.com.fiap.workshop_management_system.registration.vehicle.application.api.VehicleAvailabilityApi;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.VehicleEligibility;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegistrationVehicleEligibilityAdapterTest {

    @ParameterizedTest
    @EnumSource(VehicleAvailability.class)
    void mapsEveryPublicAvailabilityToTheConsumerEnum(VehicleAvailability availability) {
        VehicleAvailabilityApi api = mock(VehicleAvailabilityApi.class);
        RegistrationVehicleEligibilityAdapter adapter = new RegistrationVehicleEligibilityAdapter(api);
        UUID vehicleId = UUID.randomUUID();
        when(api.checkForNewWork(vehicleId)).thenReturn(availability);

        VehicleEligibility result = adapter.checkForNewWork(vehicleId);

        assertEquals(VehicleEligibility.valueOf(availability.name()), result);
    }
}
