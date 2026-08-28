package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.persistence;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port
        .CatalogServiceExecutionTimeAggregate;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.ExecutionTimeAggregate;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port
        .ServiceExecutionTimeMetricsReadPort;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ExecutionTimePeriod;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceExecutionStatus;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Repository
public class ServiceExecutionTimeMetricsQueryAdapter implements ServiceExecutionTimeMetricsReadPort {

    private static final BigDecimal NANOS_PER_HOUR = new BigDecimal("3600000000000");
    private static final int INTERNAL_SCALE = 8;

    private static final String GLOBAL_QUERY = """
            select count(execution.id),
                   avg(timestampdiff(nanosecond, execution.startedAt, execution.completedAt))
              from ServiceExecutionJpaEntity execution
             where execution.status = :completedStatus
               and execution.startedAt is not null
               and execution.completedAt is not null
               and execution.completedAt >= :fromInclusive
               and execution.completedAt < :toExclusive
            """;

    private static final String BY_CATALOG_SERVICE_QUERY = """
            select execution.catalogServiceId,
                   count(execution.id),
                   avg(timestampdiff(nanosecond, execution.startedAt, execution.completedAt))
              from ServiceExecutionJpaEntity execution
             where execution.status = :completedStatus
               and execution.catalogServiceId is not null
               and execution.startedAt is not null
               and execution.completedAt is not null
               and execution.completedAt >= :fromInclusive
               and execution.completedAt < :toExclusive
             group by execution.catalogServiceId
             order by execution.catalogServiceId
            """;

    private final EntityManager entityManager;

    public ServiceExecutionTimeMetricsQueryAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public ExecutionTimeAggregate findGlobal(ExecutionTimePeriod period) {
        Object[] row = applyPeriod(entityManager.createQuery(GLOBAL_QUERY, Object[].class), period)
                .getSingleResult();
        long sampleCount = ((Number) row[0]).longValue();
        return new ExecutionTimeAggregate(sampleCount, toHours(sampleCount, row[1]));
    }

    @Override
    public List<CatalogServiceExecutionTimeAggregate> findByCatalogService(ExecutionTimePeriod period) {
        return applyPeriod(entityManager.createQuery(BY_CATALOG_SERVICE_QUERY, Object[].class), period)
                .getResultList()
                .stream()
                .map(row -> {
                    long sampleCount = ((Number) row[1]).longValue();
                    return new CatalogServiceExecutionTimeAggregate(
                            (UUID) row[0], sampleCount, toHours(sampleCount, row[2]));
                })
                .toList();
    }

    private jakarta.persistence.TypedQuery<Object[]> applyPeriod(
            jakarta.persistence.TypedQuery<Object[]> query,
            ExecutionTimePeriod period) {
        return query
                .setParameter("completedStatus", ServiceExecutionStatus.COMPLETED)
                .setParameter("fromInclusive", period.fromInclusive())
                .setParameter("toExclusive", period.toExclusive());
    }

    private BigDecimal toHours(long sampleCount, Object averageNanoseconds) {
        if (sampleCount == 0 || averageNanoseconds == null) {
            return null;
        }
        BigDecimal nanoseconds = new BigDecimal(averageNanoseconds.toString());
        return nanoseconds.divide(NANOS_PER_HOUR, INTERNAL_SCALE, RoundingMode.HALF_UP);
    }
}
