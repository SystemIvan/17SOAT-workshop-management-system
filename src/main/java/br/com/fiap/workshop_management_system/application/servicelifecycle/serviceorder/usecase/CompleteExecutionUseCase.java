package br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.usecase;

import br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.dto.ServiceOrderMapper;
import br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.domain.servicelifecycle.serviceorder.model.ServiceOrder;
import br.com.fiap.workshop_management_system.domain.servicelifecycle.serviceorder.repository.ServiceOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * RF22 - concluir execução de um serviço.
 */
@Service
public class CompleteExecutionUseCase {

    private final ServiceOrderRepository repository;

    public CompleteExecutionUseCase(ServiceOrderRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ServiceOrderResponse execute(UUID serviceOrderId, UUID serviceExecutionId) {
        ServiceOrder serviceOrder = ServiceOrderFinder.getOrThrow(repository, serviceOrderId);
        serviceOrder.completeExecution(serviceExecutionId);
        repository.save(serviceOrder);
        return ServiceOrderMapper.toResponse(serviceOrder);
    }
}
