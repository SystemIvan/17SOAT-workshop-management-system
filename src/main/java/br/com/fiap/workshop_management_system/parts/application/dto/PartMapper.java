package br.com.fiap.workshop_management_system.parts.application.dto;

import br.com.fiap.workshop_management_system.parts.domain.model.Part;
import br.com.fiap.workshop_management_system.parts.domain.model.Price;

/**
 * Converts between the Part aggregate and the application-layer DTOs.
 * Entities never cross the controller boundary directly.
 */
public final class PartMapper {

    private PartMapper() {
    }

    public static PartResponse toResponse(Part part) {
        return new PartResponse(part.id(), part.name(), part.sku(), part.quantity().value(), toPriceDTO(part.price()));
    }

    public static Price toPrice(PriceDTO dto) {
        return new Price(dto.value());
    }

    public static PriceDTO toPriceDTO(Price price) {
        return new PriceDTO(price.value());
    }
}
