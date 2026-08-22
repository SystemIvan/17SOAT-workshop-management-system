package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderMapper;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.StockRequirementRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * RF12 - anexar uma necessidade de peça/consumível/insumo (StockRequirement) a uma ServiceExecution
 * já existente.
 */
@Service
public class AttachStockRequirementUseCase {

    private final ServiceOrderRepository repository;

    public AttachStockRequirementUseCase(ServiceOrderRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ServiceOrderResponse execute(UUID serviceOrderId, UUID executionId, StockRequirementRequest request) {
        ServiceOrder serviceOrder = ServiceOrderFinder.getOrThrowForUpdate(repository, serviceOrderId);
        serviceOrder.attachStockRequirement(executionId, ServiceOrderMapper.toStockRequirement(request));
        repository.save(serviceOrder);
        return ServiceOrderMapper.toResponse(serviceOrder);
    }
}
