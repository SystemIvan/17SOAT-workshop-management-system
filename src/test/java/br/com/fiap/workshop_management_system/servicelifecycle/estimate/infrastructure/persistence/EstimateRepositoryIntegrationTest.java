package br.com.fiap.workshop_management_system.servicelifecycle.estimate.infrastructure.persistence;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.Estimate;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateLine;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.repository.EstimateRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RF40 - lookup of the estimates of a service order by status, used to find the one awaiting the customer's
 * decision.
 *
 * <p>Runs inside a transaction, as the calling use case does: estimate lines are lazily loaded and repository
 * adapters in this project never open transactions themselves. Rolled back after each test.
 */
@SpringBootTest
@Transactional
class EstimateRepositoryIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-26T12:00:00Z");

    @Autowired
    private EstimateRepository repository;

    @Test
    void findsOnlyTheEstimatesOfTheServiceOrderInTheRequestedStatus() {
        UUID serviceOrderId = UUID.randomUUID();
        Estimate sent = persist(serviceOrderId, EstimateStatus.SENT);
        persist(serviceOrderId, EstimateStatus.CLOSED);
        persist(UUID.randomUUID(), EstimateStatus.SENT);

        List<Estimate> found = repository.findByServiceOrderIdAndStatus(serviceOrderId, EstimateStatus.SENT);

        assertEquals(1, found.size());
        assertEquals(sent.id(), found.get(0).id());
        assertEquals(serviceOrderId, found.get(0).serviceOrderId());
        assertEquals(1, found.get(0).lines().size());
    }

    @Test
    void returnsEveryMatchingEstimateWhenTheServiceOrderHasMoreThanOne() {
        UUID serviceOrderId = UUID.randomUUID();
        persist(serviceOrderId, EstimateStatus.SENT);
        persist(serviceOrderId, EstimateStatus.SENT);

        assertEquals(2, repository.findByServiceOrderIdAndStatus(serviceOrderId, EstimateStatus.SENT).size());
    }

    @Test
    void returnsAnEmptyListWhenNothingMatches() {
        UUID serviceOrderId = UUID.randomUUID();
        persist(serviceOrderId, EstimateStatus.EXPIRED);

        assertTrue(repository.findByServiceOrderIdAndStatus(serviceOrderId, EstimateStatus.SENT).isEmpty());
        assertTrue(repository.findByServiceOrderIdAndStatus(UUID.randomUUID(), EstimateStatus.SENT).isEmpty());
    }

    private Estimate persist(UUID serviceOrderId, EstimateStatus status) {
        EstimateLine line = new EstimateLine(
                UUID.randomUUID(), "Troca de óleo", Money.brl(new BigDecimal("100.00")), List.of());
        Estimate estimate = Estimate.reconstitute(
                UUID.randomUUID(), serviceOrderId, UUID.randomUUID(), UUID.randomUUID(),
                CREATED_AT, CREATED_AT.plusSeconds(86_400), List.of(line), status);
        repository.save(estimate);
        return estimate;
    }
}
