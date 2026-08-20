package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.AssignTechnicianRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderMapper;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.repository.TechnicianRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * RF19 - confirmar atribuição de um Technician a uma ServiceExecution.
 */
@Service
public class AssignTechnicianUseCase {

    private final ServiceOrderRepository repository;
    private final TechnicianRepository technicianRepository;

    public AssignTechnicianUseCase(ServiceOrderRepository repository, TechnicianRepository technicianRepository) {
        this.repository = repository;
        this.technicianRepository = technicianRepository;
    }

    @Transactional
    public ServiceOrderResponse execute(UUID serviceOrderId, UUID serviceExecutionId, AssignTechnicianRequest request) {
        ServiceOrder serviceOrder = ServiceOrderFinder.getOrThrow(repository, serviceOrderId);
        UUID technicianId = request.technicianId();
        technicianRepository.findById(technicianId)
                .orElseThrow(() -> new NoSuchElementException("Technician not found: " + technicianId));
        serviceOrder.confirmTechnicianAssignment(serviceExecutionId, technicianId);
        repository.save(serviceOrder);
        return ServiceOrderMapper.toResponse(serviceOrder);
    }
}
