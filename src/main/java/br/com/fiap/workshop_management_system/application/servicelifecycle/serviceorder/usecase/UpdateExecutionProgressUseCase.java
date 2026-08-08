package br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.usecase;

import br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.dto.ServiceOrderMapper;
import br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.dto.UpdateExecutionProgressRequest;
import br.com.fiap.workshop_management_system.domain.servicelifecycle.serviceorder.model.ServiceOrder;
import br.com.fiap.workshop_management_system.domain.servicelifecycle.serviceorder.repository.ServiceOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * RF21 - atualizar progresso de uma execução em andamento.
 */
@Service
public class UpdateExecutionProgressUseCase {

    private final ServiceOrderRepository repository;

    public UpdateExecutionProgressUseCase(ServiceOrderRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ServiceOrderResponse execute(UUID serviceOrderId, UUID serviceExecutionId, UpdateExecutionProgressRequest request) {
        ServiceOrder serviceOrder = ServiceOrderFinder.getOrThrow(repository, serviceOrderId);
        serviceOrder.updateExecutionProgress(serviceExecutionId, request.note());
        repository.save(serviceOrder);
        return ServiceOrderMapper.toResponse(serviceOrder);
    }
}
