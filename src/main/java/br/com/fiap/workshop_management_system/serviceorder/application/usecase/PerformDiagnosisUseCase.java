package br.com.fiap.workshop_management_system.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.serviceorder.application.dto.PerformDiagnosisRequest;
import br.com.fiap.workshop_management_system.serviceorder.application.dto.ServiceOrderMapper;
import br.com.fiap.workshop_management_system.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.serviceorder.domain.model.DiagnosisItem;
import br.com.fiap.workshop_management_system.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.serviceorder.domain.repository.ServiceOrderRepository;
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
