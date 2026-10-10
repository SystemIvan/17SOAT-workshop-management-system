package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.FinalizeServiceOrderRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderMapper;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * RF24 - finalizar a Service Order (entrega do veículo ao Customer).
 *
 * <p>The Customer is notified of the resulting "Entregue" status by the generic RF52 status-change flow; the
 * dedicated RF33 notice that used to be sent from here was consolidated into it.
 */
@Service
public class FinalizeServiceOrderUseCase {

    private final ServiceOrderRepository repository;

    public FinalizeServiceOrderUseCase(ServiceOrderRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ServiceOrderResponse execute(UUID serviceOrderId, FinalizeServiceOrderRequest request) {
        ServiceOrder serviceOrder = ServiceOrderFinder.getOrThrow(repository, serviceOrderId);
        serviceOrder.finalize(request.vehicleDelivered());
        repository.save(serviceOrder);
        return ServiceOrderMapper.toResponse(serviceOrder);
    }
}
