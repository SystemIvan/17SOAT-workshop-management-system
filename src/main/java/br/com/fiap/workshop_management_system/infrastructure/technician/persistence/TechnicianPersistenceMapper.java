package br.com.fiap.workshop_management_system.infrastructure.technician.persistence;

import br.com.fiap.workshop_management_system.domain.technician.model.Technician;
import org.springframework.stereotype.Component;

/**
 * Converts between the framework-agnostic {@link Technician} aggregate and its JPA
 * projection. Reconstruction of the domain object goes through {@link Technician#reconstitute},
 * which restores exact persisted state without re-running creation rules.
 */
@Component
public class TechnicianPersistenceMapper {

    public TechnicianJpaEntity toEntity(Technician technician) {
        return new TechnicianJpaEntity(technician.id(), technician.name(), technician.status(), technician.specialties());
    }

    public Technician toDomain(TechnicianJpaEntity entity) {
        return Technician.reconstitute(entity.getId(), entity.getName(), entity.getSpecialties(), entity.getStatus());
    }
}
