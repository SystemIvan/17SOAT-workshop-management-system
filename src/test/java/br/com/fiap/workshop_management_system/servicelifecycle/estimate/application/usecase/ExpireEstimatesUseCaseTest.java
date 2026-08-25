package br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.Estimate;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateLine;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.repository.EstimateRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExpireEstimatesUseCaseTest {

    @Test
    void expiresAllSentEstimatesWhoseExpirationHasBeenReached() {
        Instant now = Instant.parse("2026-08-24T18:00:00Z");

        Estimate expiredCandidate = newSentEstimate(
                now.minusSeconds(60)
        );

        EstimateRepository repository = new InMemoryEstimateRepository(
                List.of(expiredCandidate)
        );

        ExpireEstimatesUseCase useCase = new ExpireEstimatesUseCase(
                repository,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        int expiredCount = useCase.execute();

        assertEquals(1, expiredCount);
        assertEquals(EstimateStatus.EXPIRED, expiredCandidate.status());
    }

    @Test
    void doesNothingWhenThereAreNoExpiredCandidates() {
        Instant now = Instant.parse("2026-08-24T18:00:00Z");

        EstimateRepository repository = new InMemoryEstimateRepository(
                List.of()
        );

        ExpireEstimatesUseCase useCase = new ExpireEstimatesUseCase(
                repository,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        int expiredCount = useCase.execute();

        assertEquals(0, expiredCount);
    }

    private Estimate newSentEstimate(Instant expiresAt) {
        Estimate estimate = Estimate.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2026-08-24T12:00:00Z"),
                expiresAt,
                List.of(newLine())
        );

        estimate.markSent();

        return estimate;
    }

    private EstimateLine newLine() {
        return new EstimateLine(
                UUID.randomUUID(),
                "Troca de oleo",
                Money.brl(new BigDecimal("120.00")),
                List.of()
        );
    }

    private static final class InMemoryEstimateRepository implements EstimateRepository {

        private final List<Estimate> estimates;

        private InMemoryEstimateRepository(List<Estimate> estimates) {
            this.estimates = new ArrayList<>(estimates);
        }

        @Override
        public Optional<Estimate> findById(UUID id) {
            return estimates.stream()
                    .filter(estimate -> estimate.id().equals(id))
                    .findFirst();
        }

        @Override
        public boolean existsByDiagnosisId(UUID diagnosisId) {
            return estimates.stream()
                    .anyMatch(estimate -> estimate.diagnosisId().equals(diagnosisId));
        }

        @Override
        public List<Estimate> findSentExpiredAtOrBefore(Instant now) {
            return estimates.stream()
                    .filter(estimate -> estimate.status() == EstimateStatus.SENT)
                    .filter(estimate -> estimate.expiresAt() != null)
                    .filter(estimate -> !estimate.expiresAt().isAfter(now))
                    .toList();
        }

        @Override
        public void save(Estimate estimate) {
            // In-memory object already reflects the state change.
        }
    }
}