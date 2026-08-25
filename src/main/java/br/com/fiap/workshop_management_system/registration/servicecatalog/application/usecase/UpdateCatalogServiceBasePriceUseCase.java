package br.com.fiap.workshop_management_system.registration.servicecatalog.application.usecase;

import br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto.CatalogServiceMapper;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto.CatalogServiceResponse;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto
        .UpdateCatalogServiceBasePriceRequest;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.exception
        .CatalogServiceNotFoundException;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogService;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.Money;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.repository.CatalogServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdateCatalogServiceBasePriceUseCase {

    private final CatalogServiceRepository repository;

    public UpdateCatalogServiceBasePriceUseCase(CatalogServiceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CatalogServiceResponse execute(UUID id, UpdateCatalogServiceBasePriceRequest request) {
        Money newBasePrice = CatalogServiceMapper.toMoney(request.basePrice());
        CatalogService catalogService = repository.findByIdForUpdate(id)
                .orElseThrow(CatalogServiceNotFoundException::new);

        if (catalogService.updateBasePrice(newBasePrice)) {
            repository.save(catalogService);
        }
        return CatalogServiceMapper.toResponse(catalogService);
    }
}
