package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.persistence;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderSearchCriteria;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Infrastructure adapter for the {@link ServiceOrderRepository} port, backed by JPA.
 */
@Repository
public class ServiceOrderRepositoryImpl implements ServiceOrderRepository {

    private final ServiceOrderJpaRepository jpaRepository;
    private final ServiceOrderPersistenceMapper mapper;

    public ServiceOrderRepositoryImpl(ServiceOrderJpaRepository jpaRepository, ServiceOrderPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
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
        return jpaRepository.findAll(specification).stream().map(mapper::toDomain).toList();
    }

    @Override
    public void save(ServiceOrder serviceOrder) {
        jpaRepository.save(mapper.toEntity(serviceOrder));
    }
}
