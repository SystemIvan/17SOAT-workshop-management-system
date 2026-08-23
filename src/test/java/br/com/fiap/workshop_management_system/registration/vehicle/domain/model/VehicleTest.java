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
                " Volkswagen ", " Gol ", VehicleYear.create(2026, 2026), " Prata ", null);

        assertNotNull(vehicle.id());
        assertEquals(customerId, vehicle.customerId());
        assertEquals("ABC1234", vehicle.licensePlate().value());
        assertTrue(vehicle.chassisNumber().isEmpty());
        assertEquals("Volkswagen", vehicle.brand());
        assertEquals("Gol", vehicle.model());
        assertEquals(2026, vehicle.year().value());
        assertEquals("Prata", vehicle.color());
        assertTrue(vehicle.mileage().isEmpty());
        assertTrue(vehicle.active());
    }

    @Test
    void reconstitutesArchivedVehicleWithChassis() {
        UUID id = UUID.randomUUID();
        ChassisNumber chassisNumber = new ChassisNumber("9BWZZZ377VT004251");
        Vehicle vehicle = Vehicle.reconstitute(id, UUID.randomUUID(), new LicensePlate("ABC1D23"), chassisNumber,
                "Fiat", "Argo", VehicleYear.create(2024, 2026), "Branco", new Mileage(42_500), false);

        assertEquals(id, vehicle.id());
        assertEquals(chassisNumber, vehicle.chassisNumber().orElseThrow());
        assertEquals(42_500, vehicle.mileage().orElseThrow().value());
        assertFalse(vehicle.active());
    }

    @Test
    void rejectsMissingOrOversizedRequiredData() {
        LicensePlate plate = new LicensePlate("ABC1234");
        VehicleYear year = VehicleYear.create(2026, 2026);

        assertThrows(NullPointerException.class,
                () -> Vehicle.create(null, plate, null, "Brand", "Model", year, "Color", null));
        assertThrows(IllegalArgumentException.class,
                () -> Vehicle.create(UUID.randomUUID(), plate, null, null, "Model", year, "Color", null));
        assertThrows(IllegalArgumentException.class,
                () -> Vehicle.create(UUID.randomUUID(), plate, null, " ", "Model", year, "Color", null));
        assertThrows(IllegalArgumentException.class,
                () -> Vehicle.create(UUID.randomUUID(), plate, null, "Brand", "x".repeat(101), year, "Color", null));
        assertThrows(IllegalArgumentException.class,
                () -> Vehicle.create(UUID.randomUUID(), plate, null,
                        "Brand", "Model", year, "x".repeat(51), null));
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

    @Test
    void recordsFirstAndGreaterMileageWhileTreatingEqualValueAsIdempotent() {
        Vehicle vehicle = vehicle(true, null);

        assertTrue(vehicle.recordMileage(new Mileage(0)));
        assertEquals(0, vehicle.mileage().orElseThrow().value());
        assertFalse(vehicle.recordMileage(new Mileage(0)));
        assertTrue(vehicle.recordMileage(new Mileage(42_500)));
        assertEquals(42_500, vehicle.mileage().orElseThrow().value());
    }

    @Test
    void rejectsMileageDecreaseWithoutChangingVehicle() {
        Vehicle vehicle = vehicle(true, null);
        vehicle.recordMileage(new Mileage(42_500));

        assertThrows(VehicleMileageCannotDecreaseException.class,
                () -> vehicle.recordMileage(new Mileage(42_499)));

        assertEquals(42_500, vehicle.mileage().orElseThrow().value());
        assertEquals("Volkswagen", vehicle.brand());
    }

    @Test
    void rejectsMileageUpdateForArchivedVehicle() {
        Vehicle vehicle = vehicle(false, null);

        assertThrows(VehicleArchivedException.class,
                () -> vehicle.recordMileage(new Mileage(42_500)));

        assertTrue(vehicle.mileage().isEmpty());
    }

    private static Vehicle vehicle(boolean active, String chassis) {
        ChassisNumber chassisNumber = chassis == null ? null : new ChassisNumber(chassis);
        return Vehicle.reconstitute(UUID.randomUUID(), UUID.randomUUID(), new LicensePlate("ABC1234"), chassisNumber,
                "Volkswagen", "Gol", VehicleYear.create(2026, 2026), "Prata", null, active);
    }

    private static void assertOriginalState(Vehicle vehicle) {
        assertEquals("Volkswagen", vehicle.brand());
        assertEquals("Gol", vehicle.model());
        assertEquals(2026, vehicle.year().value());
        assertEquals("Prata", vehicle.color());
        assertEquals("9BWZZZ377VT004251", vehicle.chassisNumber().orElseThrow().value());
    }
}
