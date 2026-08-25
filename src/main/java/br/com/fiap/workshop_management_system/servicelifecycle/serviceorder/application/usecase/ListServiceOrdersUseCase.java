package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderMapper;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderSearchCriteria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListServiceOrdersUseCase {

    private final ServiceOrderRepository repository;

    public ListServiceOrdersUseCase(ServiceOrderRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ServiceOrderResponse> execute(ServiceOrderSearchCriteria criteria) {
        return repository.search(criteria).stream()
                .map(ServiceOrderMapper::toResponse)
                .toList();
    }
}
