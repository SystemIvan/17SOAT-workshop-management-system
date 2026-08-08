package br.com.fiap.workshop_management_system.domain.technician.model;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregate Root. The only entry point for changing a Technician's data or availability.
 */
public class Technician {

    private final UUID id;
    private final Set<Specialty> specialties = new LinkedHashSet<>();

    private String name;
    private TechnicianStatus status;

    public static Technician create(String name, Set<Specialty> specialties) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Technician name must not be blank");
        }
        if (specialties == null || specialties.isEmpty()) {
            throw new IllegalArgumentException("Technician must have at least one specialty");
        }
        Technician technician = new Technician(UUID.randomUUID(), name);
        technician.specialties.addAll(specialties);
        technician.status = TechnicianStatus.AVAILABLE;
        return technician;
    }

    private Technician(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    /**
     * Rebuilds a Technician from previously persisted state. Used exclusively by the
     * persistence adapter - unlike {@link #create}, it does not run creation rules.
     */
    public static Technician reconstitute(UUID id, String name, Set<Specialty> specialties, TechnicianStatus status) {
        Technician technician = new Technician(id, name);
        technician.specialties.addAll(specialties);
        technician.status = status;
        return technician;
    }

    public void rename(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Technician name must not be blank");
        }
        this.name = newName;
    }

    public void markBusy() {
        if (status == TechnicianStatus.INACTIVE) {
            throw new IllegalStateException("An inactive technician cannot be marked as busy");
        }
        this.status = TechnicianStatus.BUSY;
    }

    public void markAvailable() {
        if (status == TechnicianStatus.INACTIVE) {
            throw new IllegalStateException("An inactive technician cannot be marked as available");
        }
        this.status = TechnicianStatus.AVAILABLE;
    }

    public void deactivate() {
        this.status = TechnicianStatus.INACTIVE;
    }

    public boolean hasSpecialty(Specialty specialty) {
        return specialties.contains(specialty);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Set<Specialty> specialties() {
        return Set.copyOf(specialties);
    }

    public TechnicianStatus status() {
        return status;
    }
}
