package br.com.fiap.workshop_management_system.registration.servicecatalog.application.usecase;

import br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto.CatalogServiceMapper;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto.CatalogServiceResponse;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto.CreateCatalogServiceRequest;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.exception
        .CatalogServiceNameAlreadyExistsException;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogService;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogServiceName;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.Money;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.repository.CatalogServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateCatalogServiceUseCase {

    private final CatalogServiceRepository repository;

    public CreateCatalogServiceUseCase(CatalogServiceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CatalogServiceResponse execute(CreateCatalogServiceRequest request) {
        CatalogServiceName name = new CatalogServiceName(request.name());
        Money basePrice = CatalogServiceMapper.toMoney(request.basePrice());

        repository.findActiveByName(name).ifPresent(existing -> {
            throw new CatalogServiceNameAlreadyExistsException(existing.id(), existing.name().value());
        });

        CatalogService catalogService = CatalogService.create(name, basePrice);
        repository.save(catalogService);
        return CatalogServiceMapper.toResponse(catalogService);
    }
}
