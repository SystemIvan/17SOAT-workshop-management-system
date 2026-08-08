package br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.usecase;

import br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.dto.ServiceOrderMapper;
import br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.domain.servicelifecycle.serviceorder.model.ServiceOrder;
import br.com.fiap.workshop_management_system.domain.servicelifecycle.serviceorder.repository.ServiceOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetServiceOrderUseCase {

    private final ServiceOrderRepository repository;

    public GetServiceOrderUseCase(ServiceOrderRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ServiceOrderResponse execute(UUID id) {
        ServiceOrder serviceOrder = ServiceOrderFinder.getOrThrow(repository, id);
        return ServiceOrderMapper.toResponse(serviceOrder);
    }
}
