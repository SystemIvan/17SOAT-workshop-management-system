package br.com.fiap.workshop_management_system.technician.domain.model;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TechnicianTest {

    private Technician newTechnician() {
        return Technician.create("Carlos Silva", Set.of(Specialty.MECHANICAL, Specialty.ELECTRICAL));
    }

    @Test
    void newTechnicianStartsAsAvailable() {
        Technician technician = newTechnician();

        assertEquals(TechnicianStatus.AVAILABLE, technician.status());
    }

    @Test
    void cannotCreateTechnicianWithBlankName() {
        assertThrows(IllegalArgumentException.class, () -> Technician.create(" ", Set.of(Specialty.MECHANICAL)));
    }

    @Test
    void cannotCreateTechnicianWithoutSpecialties() {
        assertThrows(IllegalArgumentException.class, () -> Technician.create("Carlos Silva", Set.of()));
    }

    @Test
    void hasSpecialtyReflectsAssignedSpecialties() {
        Technician technician = newTechnician();

        assertTrue(technician.hasSpecialty(Specialty.MECHANICAL));
        assertFalse(technician.hasSpecialty(Specialty.PAINTING));
    }

    @Test
    void markBusyAndMarkAvailableToggleStatus() {
        Technician technician = newTechnician();

        technician.markBusy();
        assertEquals(TechnicianStatus.BUSY, technician.status());

        technician.markAvailable();
        assertEquals(TechnicianStatus.AVAILABLE, technician.status());
    }

    @Test
    void inactiveTechnicianCannotBeMarkedBusyOrAvailable() {
        Technician technician = newTechnician();
        technician.deactivate();

        assertEquals(TechnicianStatus.INACTIVE, technician.status());
        assertThrows(IllegalStateException.class, technician::markBusy);
        assertThrows(IllegalStateException.class, technician::markAvailable);
    }

    @Test
    void renameUpdatesNameButRejectsBlank() {
        Technician technician = newTechnician();

        technician.rename("Carlos Souza");
        assertEquals("Carlos Souza", technician.name());
        assertThrows(IllegalArgumentException.class, () -> technician.rename(""));
    }

    @Test
    void reconstituteRestoresExactPersistedState() {
        UUID id = UUID.randomUUID();
        Set<Specialty> specialties = Set.of(Specialty.BODYWORK, Specialty.PAINTING);

        Technician technician = Technician.reconstitute(id, "Ana Lima", specialties, TechnicianStatus.BUSY);

        assertEquals(id, technician.id());
        assertEquals("Ana Lima", technician.name());
        assertEquals(specialties, technician.specialties());
        assertEquals(TechnicianStatus.BUSY, technician.status());
    }
}
