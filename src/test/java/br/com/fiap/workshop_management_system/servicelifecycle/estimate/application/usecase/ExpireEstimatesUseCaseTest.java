package br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.Estimate;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateLine;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.repository.EstimateRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.DiagnosisItem;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrderStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.VehicleSnapshot;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpireEstimatesUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-24T18:00:00Z");

    @Test
    void expiresAllSentEstimatesWhoseExpirationHasBeenReached() {
        Estimate expiredCandidate = newSentEstimate(UUID.randomUUID(), NOW.minusSeconds(60));
        ExpireEstimatesUseCase useCase = useCase(
                new InMemoryEstimateRepository(List.of(expiredCandidate)), new InMemoryServiceOrderRepository());

        int expiredCount = useCase.execute();

        assertEquals(1, expiredCount);
        assertEquals(EstimateStatus.EXPIRED, expiredCandidate.status());
    }

    @Test
    void doesNothingWhenThereAreNoExpiredCandidates() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        ExpireEstimatesUseCase useCase = useCase(new InMemoryEstimateRepository(List.of()), serviceOrders);

        int expiredCount = useCase.execute();

        assertEquals(0, expiredCount);
        assertEquals(0, serviceOrders.saveCount);
    }

    @Test
    void returnsTheServiceOrderToDiagnosisWhenItsSentEstimateExpires() {
        ServiceOrder serviceOrder = serviceOrderAwaitingApproval();
        Estimate expired = newSentEstimate(serviceOrder.id(), NOW.minusSeconds(60));
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository(serviceOrder);

        useCase(new InMemoryEstimateRepository(List.of(expired)), serviceOrders).execute();

        ServiceOrder saved = serviceOrders.findById(serviceOrder.id()).orElseThrow();
        assertEquals(ServiceOrderStatus.IN_DIAGNOSIS, saved.status());
        assertEquals(1, serviceOrders.saveCount);
    }

    @Test
    void anotherSentEstimateKeepsTheServiceOrderAwaitingApproval() {
        ServiceOrder serviceOrder = serviceOrderAwaitingApproval();
        Estimate expired = newSentEstimate(serviceOrder.id(), NOW.minusSeconds(60));
        Estimate stillValid = newSentEstimate(serviceOrder.id(), NOW.plusSeconds(3600));
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository(serviceOrder);

        useCase(new InMemoryEstimateRepository(List.of(expired, stillValid)), serviceOrders).execute();

        assertEquals(EstimateStatus.EXPIRED, expired.status());
        assertEquals(ServiceOrderStatus.AWAITING_APPROVAL,
                serviceOrders.findById(serviceOrder.id()).orElseThrow().status());
        assertEquals(0, serviceOrders.saveCount);
    }

    @Test
    void reevaluatesEachServiceOrderOnceWhenSeveralOfItsEstimatesExpireTogether() {
        ServiceOrder serviceOrder = serviceOrderAwaitingApproval();
        Estimate first = newSentEstimate(serviceOrder.id(), NOW.minusSeconds(120));
        Estimate second = newSentEstimate(serviceOrder.id(), NOW.minusSeconds(60));
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository(serviceOrder);

        int expiredCount = useCase(new InMemoryEstimateRepository(List.of(first, second)), serviceOrders).execute();

        assertEquals(2, expiredCount);
        assertEquals(1, serviceOrders.lockCount);
        assertEquals(1, serviceOrders.saveCount);
        assertEquals(ServiceOrderStatus.IN_DIAGNOSIS,
                serviceOrders.findById(serviceOrder.id()).orElseThrow().status());
    }

    @Test
    void warnsWithIdsOnlyAndKeepsGoingWhenAServiceOrderIsMissing() {
        UUID missingServiceOrderId = UUID.randomUUID();
        ServiceOrder serviceOrder = serviceOrderAwaitingApproval();
        Estimate orphan = newSentEstimate(missingServiceOrderId, NOW.minusSeconds(120));
        Estimate expired = newSentEstimate(serviceOrder.id(), NOW.minusSeconds(60));
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository(serviceOrder);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Logger logger = (Logger) LoggerFactory.getLogger(ExpireEstimatesUseCase.class);
        logger.addAppender(appender);
        try {
            useCase(new InMemoryEstimateRepository(List.of(orphan, expired)), serviceOrders).execute();
        } finally {
            logger.detachAppender(appender);
        }

        assertEquals(EstimateStatus.EXPIRED, orphan.status());
        assertEquals(ServiceOrderStatus.IN_DIAGNOSIS,
                serviceOrders.findById(serviceOrder.id()).orElseThrow().status());
        assertEquals(1, appender.list.size());
        assertEquals(Level.WARN, appender.list.get(0).getLevel());
        assertTrue(appender.list.get(0).getFormattedMessage().contains(missingServiceOrderId.toString()));
    }

    private ExpireEstimatesUseCase useCase(
            EstimateRepository estimates, InMemoryServiceOrderRepository serviceOrders) {
        return new ExpireEstimatesUseCase(estimates, serviceOrders, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private ServiceOrder serviceOrderAwaitingApproval() {
        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015),
                "Initial assessment");
        serviceOrder.assignDiagnosisAssignee(UUID.randomUUID());
        serviceOrder.performDiagnosis(
                List.of(new DiagnosisItem(UUID.randomUUID(), "Troca de oleo", Money.brl(BigDecimal.TEN), List.of())),
                UUID.randomUUID(), Instant.EPOCH);
        serviceOrder.markEstimateSentWithPendingLines();
        assertEquals(ServiceOrderStatus.AWAITING_APPROVAL, serviceOrder.status());
        return serviceOrder;
    }

    private Estimate newSentEstimate(UUID serviceOrderId, Instant expiresAt) {
        Estimate estimate = Estimate.create(
                serviceOrderId,
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

    private static final class InMemoryServiceOrderRepository implements ServiceOrderRepository {

        private final Map<UUID, ServiceOrder> byId = new HashMap<>();
        private int lockCount = 0;
        private int saveCount = 0;

        private InMemoryServiceOrderRepository(ServiceOrder... orders) {
            for (ServiceOrder order : orders) {
                byId.put(order.id(), order);
            }
        }

        @Override
        public Optional<ServiceOrder> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<ServiceOrder> findByIdForUpdate(UUID id) {
            lockCount++;
            return findById(id);
        }

        @Override
        public void save(ServiceOrder serviceOrder) {
            saveCount++;
            byId.put(serviceOrder.id(), serviceOrder);
        }
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
        public List<Estimate> findByServiceOrderIdAndStatus(UUID serviceOrderId, EstimateStatus status) {
            return estimates.stream()
                    .filter(estimate -> estimate.serviceOrderId().equals(serviceOrderId))
                    .filter(estimate -> estimate.status() == status)
                    .toList();
        }

        @Override
        public void save(Estimate estimate) {
            // In-memory object already reflects the state change.
        }
    }
}
