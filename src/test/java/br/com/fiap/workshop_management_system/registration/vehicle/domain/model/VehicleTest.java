package br.com.fiap.workshop_management_system.registration.vehicle.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleTest {

    @Test
    void createsActiveVehicleWithOwnIdAndOptionalChassis() {
        UUID customerId = UUID.randomUUID();
        Vehicle vehicle = Vehicle.create(customerId, new LicensePlate("ABC-1234"), null,
                " Volkswagen ", " Gol ", VehicleYear.create(2026, 2026), " Prata ");

        assertNotNull(vehicle.id());
        assertEquals(customerId, vehicle.customerId());
        assertEquals("ABC1234", vehicle.licensePlate().value());
        assertTrue(vehicle.chassisNumber().isEmpty());
        assertEquals("Volkswagen", vehicle.brand());
        assertEquals("Gol", vehicle.model());
        assertEquals(2026, vehicle.year().value());
        assertEquals("Prata", vehicle.color());
        assertTrue(vehicle.active());
    }

    @Test
    void reconstitutesArchivedVehicleWithChassis() {
        UUID id = UUID.randomUUID();
        ChassisNumber chassisNumber = new ChassisNumber("9BWZZZ377VT004251");
        Vehicle vehicle = Vehicle.reconstitute(id, UUID.randomUUID(), new LicensePlate("ABC1D23"), chassisNumber,
                "Fiat", "Argo", VehicleYear.create(2024, 2026), "Branco", false);

        assertEquals(id, vehicle.id());
        assertEquals(chassisNumber, vehicle.chassisNumber().orElseThrow());
        assertFalse(vehicle.active());
    }

    @Test
    void rejectsMissingOrOversizedRequiredData() {
        LicensePlate plate = new LicensePlate("ABC1234");
        VehicleYear year = VehicleYear.create(2026, 2026);

        assertThrows(NullPointerException.class,
                () -> Vehicle.create(null, plate, null, "Brand", "Model", year, "Color"));
        assertThrows(IllegalArgumentException.class,
                () -> Vehicle.create(UUID.randomUUID(), plate, null, " ", "Model", year, "Color"));
        assertThrows(IllegalArgumentException.class,
                () -> Vehicle.create(UUID.randomUUID(), plate, null, "Brand", "x".repeat(101), year, "Color"));
        assertThrows(IllegalArgumentException.class,
                () -> Vehicle.create(UUID.randomUUID(), plate, null, "Brand", "Model", year, "x".repeat(51)));
    }

    @Test
    void updatesDescriptionsAndChassisAtomically() {
        Vehicle vehicle = vehicle(true, "9BWZZZ377VT004251");
        ChassisNumber newChassis = new ChassisNumber("9BWZZZ377VT004252");

        vehicle.updateDetails(" Fiat ", " Argo ", VehicleYear.create(2025, 2026), " Branco ", newChassis);

        assertEquals("Fiat", vehicle.brand());
        assertEquals("Argo", vehicle.model());
        assertEquals(2025, vehicle.year().value());
        assertEquals("Branco", vehicle.color());
        assertEquals(newChassis, vehicle.chassisNumber().orElseThrow());
    }

    @Test
    void preservesChassisWhenNoUpdateIsRequested() {
        Vehicle vehicle = vehicle(true, "9BWZZZ377VT004251");

        vehicle.updateDetails("Fiat", "Argo", VehicleYear.create(2025, 2026), "Branco", null);

        assertEquals("9BWZZZ377VT004251", vehicle.chassisNumber().orElseThrow().value());
    }

    @Test
    void rejectsArchivedVehicleWithoutChangingState() {
        Vehicle vehicle = vehicle(false, "9BWZZZ377VT004251");

        assertThrows(VehicleArchivedException.class,
                () -> vehicle.updateDetails("Fiat", "Argo", VehicleYear.create(2025, 2026), "Branco",
                        new ChassisNumber("9BWZZZ377VT004252")));

        assertOriginalState(vehicle);
    }

    @Test
    void rejectsInvalidUpdateWithoutChangingAnyField() {
        Vehicle vehicle = vehicle(true, "9BWZZZ377VT004251");

        assertThrows(IllegalArgumentException.class,
                () -> vehicle.updateDetails("Fiat", " ", VehicleYear.create(2025, 2026), "Branco",
                        new ChassisNumber("9BWZZZ377VT004252")));

        assertOriginalState(vehicle);
    }

    private static Vehicle vehicle(boolean active, String chassis) {
        ChassisNumber chassisNumber = chassis == null ? null : new ChassisNumber(chassis);
        return Vehicle.reconstitute(UUID.randomUUID(), UUID.randomUUID(), new LicensePlate("ABC1234"), chassisNumber,
                "Volkswagen", "Gol", VehicleYear.create(2026, 2026), "Prata", active);
    }

    private static void assertOriginalState(Vehicle vehicle) {
        assertEquals("Volkswagen", vehicle.brand());
        assertEquals("Gol", vehicle.model());
        assertEquals(2026, vehicle.year().value());
        assertEquals("Prata", vehicle.color());
        assertEquals("9BWZZZ377VT004251", vehicle.chassisNumber().orElseThrow().value());
    }
}
