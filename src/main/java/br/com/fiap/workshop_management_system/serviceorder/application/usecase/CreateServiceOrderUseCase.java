package br.com.fiap.workshop_management_system.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.serviceorder.application.dto.CreateServiceOrderRequest;
import br.com.fiap.workshop_management_system.serviceorder.application.dto.ServiceOrderMapper;
import br.com.fiap.workshop_management_system.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.serviceorder.domain.model.Priority;
import br.com.fiap.workshop_management_system.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.serviceorder.domain.model.VehicleSnapshot;
import br.com.fiap.workshop_management_system.serviceorder.domain.repository.ServiceOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateServiceOrderUseCase {

    private final ServiceOrderRepository repository;

    public CreateServiceOrderUseCase(ServiceOrderRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ServiceOrderResponse execute(CreateServiceOrderRequest request) {
        VehicleSnapshot vehicleSnapshot = ServiceOrderMapper.toVehicleSnapshot(request.vehicleSnapshot());
        Priority priority = request.priority() != null ? request.priority() : Priority.NORMAL;
        ServiceOrder serviceOrder = ServiceOrder.create(request.customerId(), request.vehicleId(), vehicleSnapshot, priority);
        repository.save(serviceOrder);
        return ServiceOrderMapper.toResponse(serviceOrder);
    }
}
