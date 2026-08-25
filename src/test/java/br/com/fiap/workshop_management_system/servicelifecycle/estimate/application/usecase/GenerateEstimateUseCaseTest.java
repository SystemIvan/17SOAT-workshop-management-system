package br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.Estimate;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.repository.EstimateRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.DiagnosisItem;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockItemType;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockRequirement;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.VehicleSnapshot;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GenerateEstimateUseCaseTest {

    private static final Instant NOW =
            Instant.parse("2026-08-16T15:00:00Z");

    @Test
    void generatesEstimateFromOpenDiagnosis() {
        UUID customerId = UUID.randomUUID();

        ServiceOrder serviceOrder = ServiceOrder.create(
                customerId,
                UUID.randomUUID(),
                new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015),
                "Initial assessment"
        );

        StockRequirement stock = new StockRequirement(
                UUID.randomUUID(),
                StockItemType.PART,
                2,
                "Filtro de oleo",
                Money.brl(new BigDecimal("35.90")),
                false
        );

        DiagnosisItem item = new DiagnosisItem(
                UUID.randomUUID(),
                "Troca de oleo",
                Money.brl(new BigDecimal("120.00")),
                List.of(stock)
        );

        serviceOrder.assignDiagnosisAssignee(UUID.randomUUID());
        serviceOrder.performDiagnosis(
                List.of(item),
                UUID.randomUUID(),
                java.time.Instant.EPOCH
        );

        UUID diagnosisId = serviceOrder.openDiagnosisId();

        InMemoryServiceOrderRepository serviceOrders =
                new InMemoryServiceOrderRepository(serviceOrder);

        InMemoryEstimateRepository estimates =
                new InMemoryEstimateRepository();

        GenerateEstimateUseCase useCase =
                new GenerateEstimateUseCase(
                        serviceOrders,
                        estimates,
                        Clock.fixed(NOW, ZoneOffset.UTC)
                );

        GenerateEstimateUseCase.Result result =
                useCase.execute(serviceOrder.id(), diagnosisId);

        Estimate estimate = result.estimate();

        assertNotNull(estimate.id());
        assertEquals(serviceOrder.id(), estimate.serviceOrderId());
        assertEquals(diagnosisId, estimate.diagnosisId());
        assertEquals(customerId, estimate.customerId());
        assertEquals(NOW, estimate.createdAt());
        assertEquals(
                NOW.plusSeconds(48 * 60 * 60),
                estimate.expiresAt()
        );

        assertEquals(1, estimate.lines().size());
        assertEquals(
                "Troca de oleo",
                estimate.lines().getFirst().serviceName()
        );
        assertEquals(
                Money.brl(new BigDecimal("120.00")),
                estimate.lines().getFirst().servicePrice()
        );

        assertEquals(
                1,
                estimate.lines().getFirst().stockItems().size()
        );
        assertEquals(
                "Filtro de oleo",
                estimate.lines()
                        .getFirst()
                        .stockItems()
                        .getFirst()
                        .nameSnapshot()
        );

        assertEquals(
                estimate.id(),
                result.event().estimateId()
        );
        assertEquals(
                customerId,
                result.event().customerId()
        );
        assertEquals(
                estimate.expiresAt(),
                result.event().expiresAt()
        );

        assertTrue(
                estimates.findById(estimate.id()).isPresent()
        );
        assertEquals(
                EstimateStatus.SENT,
                estimates.findById(estimate.id())
                        .orElseThrow()
                        .status()
        );

        // Gerar orçamento não significa aprovação do cliente.
        assertEquals(
                diagnosisId,
                serviceOrder.openDiagnosisId()
        );
        assertTrue(
                serviceOrder.serviceExecutions()
                        .getFirst()
                        .stockRequirementsFrozen()
        );
    }

    @Test
    void publishesEstimateGeneratedEventOnSuccess() {
        ServiceOrder serviceOrder = diagnosedServiceOrder();
        UUID diagnosisId = serviceOrder.openDiagnosisId();

        ApplicationEventPublisher eventPublisher =
                mock(ApplicationEventPublisher.class);

        GenerateEstimateUseCase useCase =
                new GenerateEstimateUseCase(
                        new InMemoryServiceOrderRepository(serviceOrder),
                        new InMemoryEstimateRepository(),
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        eventPublisher
                );

        GenerateEstimateUseCase.Result result =
                useCase.execute(serviceOrder.id(), diagnosisId);

        verify(eventPublisher).publishEvent(result.event());
    }

    @Test
    void rejectsDuplicateEstimateForSameDiagnosis() {
        ServiceOrder serviceOrder = diagnosedServiceOrder();

        InMemoryServiceOrderRepository serviceOrders =
                new InMemoryServiceOrderRepository(serviceOrder);

        InMemoryEstimateRepository estimates =
                new InMemoryEstimateRepository();

        GenerateEstimateUseCase useCase =
                new GenerateEstimateUseCase(
                        serviceOrders,
                        estimates,
                        Clock.fixed(NOW, ZoneOffset.UTC)
                );

        UUID diagnosisId = serviceOrder.openDiagnosisId();

        useCase.execute(serviceOrder.id(), diagnosisId);

        assertThrows(
                IllegalStateException.class,
                () -> useCase.execute(
                        serviceOrder.id(),
                        diagnosisId
                )
        );
    }

    @Test
    void rejectsDiagnosisThatIsNotOpen() {
        ServiceOrder serviceOrder = diagnosedServiceOrder();

        GenerateEstimateUseCase useCase =
                new GenerateEstimateUseCase(
                        new InMemoryServiceOrderRepository(serviceOrder),
                        new InMemoryEstimateRepository(),
                        Clock.fixed(NOW, ZoneOffset.UTC)
                );

        assertThrows(
                IllegalStateException.class,
                () -> useCase.execute(
                        serviceOrder.id(),
                        UUID.randomUUID()
                )
        );
    }

    @Test
    void rejectsUnknownServiceOrder() {
        GenerateEstimateUseCase useCase =
                new GenerateEstimateUseCase(
                        new InMemoryServiceOrderRepository(),
                        new InMemoryEstimateRepository(),
                        Clock.fixed(NOW, ZoneOffset.UTC)
                );

        assertThrows(
                java.util.NoSuchElementException.class,
                () -> useCase.execute(
                        UUID.randomUUID(),
                        UUID.randomUUID()
                )
        );
    }

    private ServiceOrder diagnosedServiceOrder() {
        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new VehicleSnapshot(
                        "ABC1D23",
                        "Fiat",
                        "Uno",
                        2015
                ),
                "Initial assessment"
        );

        DiagnosisItem item = new DiagnosisItem(
                UUID.randomUUID(),
                "Alinhamento",
                Money.brl(BigDecimal.TEN),
                List.of()
        );

        serviceOrder.assignDiagnosisAssignee(
                UUID.randomUUID()
        );

        serviceOrder.performDiagnosis(
                List.of(item),
                UUID.randomUUID(),
                java.time.Instant.EPOCH
        );

        return serviceOrder;
    }

    private static final class InMemoryServiceOrderRepository
            implements ServiceOrderRepository {

        private final Map<UUID, ServiceOrder> data =
                new HashMap<>();

        private InMemoryServiceOrderRepository(
                ServiceOrder... orders) {

            for (ServiceOrder order : orders) {
                data.put(order.id(), order);
            }
        }

        @Override
        public Optional<ServiceOrder> findById(UUID id) {
            return Optional.ofNullable(data.get(id));
        }

        @Override
        public void save(ServiceOrder serviceOrder) {
            data.put(serviceOrder.id(), serviceOrder);
        }
    }

    private static final class InMemoryEstimateRepository
            implements EstimateRepository {

        private final Map<UUID, Estimate> data =
                new HashMap<>();

        @Override
        public Optional<Estimate> findById(UUID id) {
            return Optional.ofNullable(data.get(id));
        }

        @Override
        public boolean existsByDiagnosisId(UUID diagnosisId) {
            return data.values().stream()
                    .anyMatch(estimate ->
                            estimate.diagnosisId()
                                    .equals(diagnosisId));
        }

        @Override
        public List<Estimate> findSentExpiredAtOrBefore(
                Instant now) {

            return data.values().stream()
                    .filter(estimate ->
                            estimate.status()
                                    == EstimateStatus.SENT)
                    .filter(estimate ->
                            estimate.expiresAt() != null)
                    .filter(estimate ->
                            !estimate.expiresAt()
                                    .isAfter(now))
                    .toList();
        }

        @Override
        public void save(Estimate estimate) {
            data.put(estimate.id(), estimate);
        }
    }
}