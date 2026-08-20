package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.CreateServiceOrderRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderMapper;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.TechnicianNotificationPort;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Priority;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.VehicleSnapshot;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.model.Technician;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.model.TechnicianStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.repository.TechnicianRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateServiceOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateServiceOrderUseCase.class);

    private final ServiceOrderRepository repository;
    private final TechnicianRepository technicianRepository;
    private final TechnicianNotificationPort technicianNotificationPort;

    public CreateServiceOrderUseCase(ServiceOrderRepository repository, TechnicianRepository technicianRepository,
            TechnicianNotificationPort technicianNotificationPort) {
        this.repository = repository;
        this.technicianRepository = technicianRepository;
        this.technicianNotificationPort = technicianNotificationPort;
    }

    @Transactional
    public ServiceOrderResponse execute(CreateServiceOrderRequest request) {
        VehicleSnapshot vehicleSnapshot = ServiceOrderMapper.toVehicleSnapshot(request.vehicleSnapshot());
        Priority priority = request.priority() != null ? request.priority() : Priority.NORMAL;
        ServiceOrder serviceOrder = ServiceOrder.create(request.customerId(), request.vehicleId(), vehicleSnapshot, priority);
        repository.save(serviceOrder);
        notifyActiveTechnicians(serviceOrder);
        return ServiceOrderMapper.toResponse(serviceOrder);
    }

    private void notifyActiveTechnicians(ServiceOrder serviceOrder) {
        technicianRepository.findAll().stream()
                .filter(technician -> technician.status() != TechnicianStatus.INACTIVE)
                .forEach(technician -> notifyTechnician(serviceOrder, technician));
    }

    private void notifyTechnician(ServiceOrder serviceOrder, Technician technician) {
        try {
            technicianNotificationPort.notifyServiceOrderCreated(serviceOrder.id(), technician.id());
        } catch (RuntimeException ex) {
            log.warn("Failed to notify technician {} about created service order {}",
                    technician.id(), serviceOrder.id(), ex);
        }
    }
}
