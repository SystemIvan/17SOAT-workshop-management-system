package br.com.fiap.workshop_management_system.servicelifecycle.estimate.infrastructure.persistence;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.Estimate;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateLine;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EstimatePersistenceMapperTest {

    private final EstimatePersistenceMapper mapper = new EstimatePersistenceMapper();

    @ParameterizedTest
    @EnumSource(EstimateStatus.class)
    void roundTripsEveryEstimateStatus(EstimateStatus status) {
        Estimate estimate = Estimate.reconstitute(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2026-08-20T12:00:00Z"),
                Instant.parse("2026-08-22T12:00:00Z"),
                List.of(new EstimateLine(
                        UUID.randomUUID(), "Troca de oleo", Money.brl(new BigDecimal("120.00")), List.of())),
                status);

        EstimateJpaEntity entity = mapper.toEntity(estimate);
        Estimate reconstituted = mapper.toDomain(entity);

        assertEquals(status, entity.getStatus());
        assertEquals(status, reconstituted.status());
    }
}
