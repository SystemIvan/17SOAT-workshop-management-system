package br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.usecase;

import br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.dto.PerformDiagnosisRequest;
import br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.dto.ServiceOrderMapper;
import br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.domain.servicelifecycle.serviceorder.model.DiagnosisItem;
import br.com.fiap.workshop_management_system.domain.servicelifecycle.serviceorder.model.ServiceOrder;
import br.com.fiap.workshop_management_system.domain.servicelifecycle.serviceorder.repository.ServiceOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PerformDiagnosisUseCase {

    private final ServiceOrderRepository repository;

    public PerformDiagnosisUseCase(ServiceOrderRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ServiceOrderResponse execute(UUID serviceOrderId, PerformDiagnosisRequest request) {
        ServiceOrder serviceOrder = ServiceOrderFinder.getOrThrow(repository, serviceOrderId);
        List<DiagnosisItem> items = ServiceOrderMapper.toDiagnosisItems(request.items());
        serviceOrder.performDiagnosis(items);
        repository.save(serviceOrder);
        return ServiceOrderMapper.toResponse(serviceOrder);
    }
}
