package br.com.fiap.workshop_management_system.servicelifecycle.technician.infrastructure.persistence;

import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.model.Specialty;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.model.TechnicianStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * JPA projection of the {@link br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.model.Technician}
 * aggregate. Kept separate from the domain class so the domain stays framework-agnostic.
 *
 * <p>Implements {@link Persistable} because the id (UUID) is always assigned by the domain
 * before persistence - without this, Spring Data would assume every save() is an update.
 */
@Entity
@Table(name = "technicians")
public class TechnicianJpaEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    private String name;

    @Enumerated(EnumType.STRING)
    private TechnicianStatus status;

    @ElementCollection
    @CollectionTable(name = "technician_specialties", joinColumns = @JoinColumn(name = "technician_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "specialty")
    private Set<Specialty> specialties = new LinkedHashSet<>();

    protected TechnicianJpaEntity() {
    }

    public TechnicianJpaEntity(UUID id, String name, TechnicianStatus status, Set<Specialty> specialties) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.specialties = specialties;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return false;
    }

    public String getName() {
        return name;
    }

    public TechnicianStatus getStatus() {
        return status;
    }

    public Set<Specialty> getSpecialties() {
        return specialties;
    }
}
