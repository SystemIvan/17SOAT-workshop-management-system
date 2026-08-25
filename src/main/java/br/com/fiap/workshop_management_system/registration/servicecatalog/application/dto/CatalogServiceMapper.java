package br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto;

import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogService;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.Money;

public final class CatalogServiceMapper {

    private CatalogServiceMapper() {
    }

    public static Money toMoney(MoneyDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("O preço-base do serviço é obrigatório");
        }
        return new Money(dto.value(), dto.currency());
    }

    public static CatalogServiceResponse toResponse(CatalogService catalogService) {
        Money basePrice = catalogService.basePrice();
        return new CatalogServiceResponse(
                catalogService.id(),
                catalogService.name().value(),
                new MoneyDto(basePrice.value(), basePrice.currency()),
                catalogService.active());
    }
}
