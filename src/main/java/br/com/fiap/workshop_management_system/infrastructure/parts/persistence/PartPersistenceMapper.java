package br.com.fiap.workshop_management_system.infrastructure.parts.persistence;

import br.com.fiap.workshop_management_system.domain.parts.model.Part;
import br.com.fiap.workshop_management_system.domain.parts.model.Price;
import br.com.fiap.workshop_management_system.domain.parts.model.Quantity;
import org.springframework.stereotype.Component;

/**
 * Converts between the framework-agnostic {@link Part} aggregate and its JPA
 * projection. Reconstruction of the domain object goes through {@link Part#reconstitute},
 * which restores exact persisted state without re-running creation rules.
 */
@Component
public class PartPersistenceMapper {

    public PartJpaEntity toEntity(Part part) {
        return new PartJpaEntity(part.id(), part.name(), part.sku(), part.quantity().value(), part.price().value());
    }

    public Part toDomain(PartJpaEntity entity) {
        return Part.reconstitute(
                entity.getId(),
                entity.getName(),
                entity.getSku(),
                new Quantity(entity.getQuantity()),
                new Price(entity.getPriceValue()));
    }
}
