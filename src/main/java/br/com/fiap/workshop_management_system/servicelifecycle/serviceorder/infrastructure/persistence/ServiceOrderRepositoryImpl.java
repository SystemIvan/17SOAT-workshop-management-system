package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.persistence;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrderStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository
        .ServiceOrderSearchCriteria;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository
        .StockReservationRetryCandidate;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Infrastructure adapter for the {@link ServiceOrderRepository} port, backed by JPA.
 *
 * <p>RF52: {@link #save} is the single persistence path for every command that changes a ServiceOrder, so it is
 * also where the aggregate's status transition is published. Publishing happens inside the caller's transaction,
 * so after-commit listeners only see it if that transaction commits.
 */
@Repository
public class ServiceOrderRepositoryImpl implements ServiceOrderRepository {

    private final ServiceOrderJpaRepository jpaRepository;
    private final ServiceOrderPersistenceMapper mapper;
    private final ApplicationEventPublisher eventPublisher;

    public ServiceOrderRepositoryImpl(
            ServiceOrderJpaRepository jpaRepository,
            ServiceOrderPersistenceMapper mapper,
            ApplicationEventPublisher eventPublisher) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Optional<ServiceOrder> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<ServiceOrder> findByIdForUpdate(UUID id) {
        return jpaRepository.findByIdForUpdate(id).map(mapper::toDomain);
    }

    @Override
    public List<ServiceOrder> search(ServiceOrderSearchCriteria criteria) {
        Specification<ServiceOrderJpaEntity> specification = (root, query, builder) -> {
            query.distinct(true);
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.status() != null) {
                predicates.add(builder.equal(root.get("statusSnapshot"), criteria.status()));
            } else {
                predicates.add(root.get("statusSnapshot")
                        .in(ServiceOrderStatus.COMPLETED, ServiceOrderStatus.DELIVERED).not());
            }
            if (criteria.customerId() != null) {
                predicates.add(builder.equal(root.get("customerId"), criteria.customerId()));
            }
            if (criteria.priority() != null) {
                predicates.add(builder.equal(root.get("priority"), criteria.priority()));
            }
            if (criteria.technicianId() != null) {
                Predicate diagnosisAssignee = builder.equal(root.get("diagnosisAssigneeId"), criteria.technicianId());
                Join<ServiceOrderJpaEntity, ServiceExecutionJpaEntity> executions =
                        root.join("executions", JoinType.LEFT);
                Predicate executionAssignee =
                        builder.equal(executions.get("assignedTechnicianId"), criteria.technicianId());
                predicates.add(builder.or(diagnosisAssignee, executionAssignee));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        return jpaRepository.findAll(specification).stream()
                .map(mapper::toDomain)
                .sorted(Comparator.<ServiceOrder>comparingInt(serviceOrder -> serviceOrder.status().listingRank())
                        .thenComparing(ServiceOrder::createdAt))
                .toList();
    }

    @Override
    public List<StockReservationRetryCandidate> findAwaitingItemsByStockItemIds(
            java.util.Collection<UUID> stockItemIds) {
        if (stockItemIds == null || stockItemIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findAwaitingItemsByStockItemIds(stockItemIds);
    }

    @Override
    public void save(ServiceOrder serviceOrder) {
        jpaRepository.save(mapper.toEntity(serviceOrder));
        serviceOrder.pullStatusChange().ifPresent(eventPublisher::publishEvent);
    }
}
