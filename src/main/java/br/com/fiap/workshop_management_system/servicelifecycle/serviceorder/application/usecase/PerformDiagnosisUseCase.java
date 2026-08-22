package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.PerformDiagnosisRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderMapper;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.DiagnosisItem;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.repository.TechnicianRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class PerformDiagnosisUseCase {

    private final ServiceOrderRepository repository;
    private final TechnicianRepository technicianRepository;
    private final Clock clock;

    @Autowired
    public PerformDiagnosisUseCase(ServiceOrderRepository repository, TechnicianRepository technicianRepository) {
        this(repository, technicianRepository, Clock.systemUTC());
    }

    PerformDiagnosisUseCase(ServiceOrderRepository repository, TechnicianRepository technicianRepository, Clock clock) {
        this.repository = repository;
        this.technicianRepository = technicianRepository;
        this.clock = clock;
    }

    @Transactional
    public ServiceOrderResponse execute(UUID serviceOrderId, PerformDiagnosisRequest request) {
        technicianRepository.findById(request.diagnosedByTechnicianId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Technician not found: " + request.diagnosedByTechnicianId()));
        ServiceOrder serviceOrder = ServiceOrderFinder.getOrThrowForUpdate(repository, serviceOrderId);
        List<DiagnosisItem> items = ServiceOrderMapper.toDiagnosisItems(request.items());
        Instant diagnosedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        serviceOrder.performDiagnosis(items, request.diagnosedByTechnicianId(), diagnosedAt);
        repository.save(serviceOrder);
        return ServiceOrderMapper.toResponse(serviceOrder);
    }
}
