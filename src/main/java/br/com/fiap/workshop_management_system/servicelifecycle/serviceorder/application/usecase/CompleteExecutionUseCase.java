package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderMapper;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * RF22 - concluir execução de um serviço.
 */
@Service
public class CompleteExecutionUseCase {

    private final ServiceOrderRepository repository;
    private final Clock clock;

    @Autowired
    public CompleteExecutionUseCase(ServiceOrderRepository repository) {
        this(repository, Clock.systemUTC());
    }

    CompleteExecutionUseCase(ServiceOrderRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public ServiceOrderResponse execute(UUID serviceOrderId, UUID serviceExecutionId) {
        ServiceOrder serviceOrder = ServiceOrderFinder.getOrThrowForUpdate(repository, serviceOrderId);
        serviceOrder.completeExecution(serviceExecutionId, clock.instant().truncatedTo(ChronoUnit.MICROS));
        repository.save(serviceOrder);
        return ServiceOrderMapper.toResponse(serviceOrder);
    }
}
