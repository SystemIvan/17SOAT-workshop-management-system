package br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.usecase;

import br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.dto.ServiceOrderMapper;
import br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.dto.ServiceOrderStatusResponse;
import br.com.fiap.workshop_management_system.domain.servicelifecycle.serviceorder.model.ServiceOrder;
import br.com.fiap.workshop_management_system.domain.servicelifecycle.serviceorder.repository.ServiceOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * RF23 - consultar o status derivado (tracking) da Service Order.
 */
@Service
public class GetServiceOrderStatusUseCase {

    private final ServiceOrderRepository repository;

    public GetServiceOrderStatusUseCase(ServiceOrderRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ServiceOrderStatusResponse execute(UUID id) {
        ServiceOrder serviceOrder = ServiceOrderFinder.getOrThrow(repository, id);
        return ServiceOrderMapper.toStatusResponse(serviceOrder);
    }
}
