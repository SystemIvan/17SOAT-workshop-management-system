package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.AssignDiagnosisAssigneeRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderMapper;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.repository.TechnicianRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class AssignDiagnosisAssigneeUseCase {

    private final ServiceOrderRepository serviceOrderRepository;
    private final TechnicianRepository technicianRepository;

    public AssignDiagnosisAssigneeUseCase(
            ServiceOrderRepository serviceOrderRepository, TechnicianRepository technicianRepository) {
        this.serviceOrderRepository = serviceOrderRepository;
        this.technicianRepository = technicianRepository;
    }

    @Transactional
    public ServiceOrderResponse execute(UUID serviceOrderId, AssignDiagnosisAssigneeRequest request) {
        technicianRepository.findById(request.technicianId())
                .orElseThrow(() -> new NoSuchElementException("Technician not found: " + request.technicianId()));
        ServiceOrder serviceOrder = ServiceOrderFinder.getOrThrowForUpdate(serviceOrderRepository, serviceOrderId);
        serviceOrder.assignDiagnosisAssignee(request.technicianId());
        serviceOrderRepository.save(serviceOrder);
        return ServiceOrderMapper.toResponse(serviceOrder);
    }
}
