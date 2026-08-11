package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderMapper;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * RF20 - iniciar execução de um serviço.
 */
@Service
public class StartExecutionUseCase {

    private final ServiceOrderRepository repository;

    public StartExecutionUseCase(ServiceOrderRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ServiceOrderResponse execute(UUID serviceOrderId, UUID serviceExecutionId) {
        ServiceOrder serviceOrder = ServiceOrderFinder.getOrThrow(repository, serviceOrderId);
        serviceOrder.startExecution(serviceExecutionId);
        repository.save(serviceOrder);
        return ServiceOrderMapper.toResponse(serviceOrder);
    }
}
