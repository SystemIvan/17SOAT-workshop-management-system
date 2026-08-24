package br.com.fiap.workshop_management_system.registration.servicecatalog.infrastructure.persistence;

import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogService;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogServiceName;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CurrencyCode;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.Money;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class CatalogServicePersistenceMapper {

    public CatalogServiceJpaEntity toEntity(CatalogService catalogService) {
        return new CatalogServiceJpaEntity(
                catalogService.id(),
                catalogService.name().value(),
                normalizedNameKey(catalogService.name()),
                catalogService.basePrice().value(),
                catalogService.basePrice().currency().name(),
                catalogService.active());
    }

    public CatalogService toDomain(CatalogServiceJpaEntity entity) {
        return CatalogService.reconstitute(
                entity.getId(),
                new CatalogServiceName(entity.getName()),
                new Money(entity.getBasePriceValue(), CurrencyCode.valueOf(entity.getBasePriceCurrency())),
                entity.isActive());
    }

    public byte[] normalizedNameKey(CatalogServiceName name) {
        return name.canonicalValue().getBytes(StandardCharsets.UTF_8);
    }
}
