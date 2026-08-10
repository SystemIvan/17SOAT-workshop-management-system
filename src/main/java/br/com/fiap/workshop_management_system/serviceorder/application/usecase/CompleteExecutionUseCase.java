package br.com.fiap.workshop_management_system.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.serviceorder.application.dto.ServiceOrderMapper;
import br.com.fiap.workshop_management_system.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.serviceorder.domain.repository.ServiceOrderRepository;
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
