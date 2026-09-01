package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.FinalizeServiceOrderRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderMapper;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.CustomerNotificationPort;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * RF24 - finalizar a Service Order (entrega do veículo ao Customer).
 */
@Service
public class FinalizeServiceOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(FinalizeServiceOrderUseCase.class);

    private final ServiceOrderRepository repository;
    private final CustomerNotificationPort customerNotificationPort;

    public FinalizeServiceOrderUseCase(ServiceOrderRepository repository, CustomerNotificationPort customerNotificationPort) {
        this.repository = repository;
        this.customerNotificationPort = customerNotificationPort;
    }

    @Transactional
    public ServiceOrderResponse execute(UUID serviceOrderId, FinalizeServiceOrderRequest request) {
        ServiceOrder serviceOrder = ServiceOrderFinder.getOrThrow(repository, serviceOrderId);
        serviceOrder.finalize(request.vehicleDelivered());
        repository.save(serviceOrder);
        notifyCustomer(serviceOrder);
        return ServiceOrderMapper.toResponse(serviceOrder);
    }

    private void notifyCustomer(ServiceOrder serviceOrder) {
        try {
            customerNotificationPort.notifyServiceOrderFinalized(serviceOrder.id(), serviceOrder.customerId());
        } catch (RuntimeException ex) {
            log.warn("Failed to notify customer {} about finalized service order {}",
                    serviceOrder.customerId(), serviceOrder.id(), ex);
        }
    }
}
