package br.com.fiap.workshop_management_system.servicelifecycle.estimate.notification;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.usecase.GenerateEstimateUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.notification.application.port
        .CustomerEstimateNotificationPort;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.DiagnosisItem;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.VehicleSnapshot;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.EnableScenarios;
import org.springframework.modulith.test.Scenario;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the real servicelifecycle + registration Spring context (no mocks for the wiring itself) to
 * prove the full real pipeline: GenerateEstimateUseCase publishes EstimateGenerated through the real
 * ApplicationEventPublisher, and it reaches EstimateGeneratedNotificationListener via
 * @ApplicationModuleListener - not a hand-built event, and not just the listener's own business logic
 * (already covered by EstimateGeneratedNotificationListenerTest).
 */
@ApplicationModuleTest(ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
@EnableScenarios
class EstimateGeneratedNotificationApplicationModuleTest {

    @Autowired
    private ServiceOrderRepository serviceOrderRepository;

    @Autowired
    private GenerateEstimateUseCase generateEstimateUseCase;

    @Autowired
    private FakeCustomerEstimateNotificationPort fakePort;

    @Test
    void generatingAnEstimateReachesTheListenerThroughTheRealPipeline(Scenario scenario) {
        UUID customerId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        ServiceOrder serviceOrder = ServiceOrder.create(
                customerId,
                UUID.randomUUID(),
                new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015),
                "Initial assessment");
        DiagnosisItem item = new DiagnosisItem(
                UUID.randomUUID(), "Troca de óleo", Money.brl(BigDecimal.TEN), List.of());
        serviceOrder.assignDiagnosisAssignee(technicianId);
        serviceOrder.performDiagnosis(List.of(item), technicianId, Instant.now());
        UUID diagnosisId = serviceOrder.openDiagnosisId();
        serviceOrderRepository.save(serviceOrder);

        scenario.stimulate(() -> generateEstimateUseCase.execute(serviceOrder.id(), diagnosisId))
                .andWaitForStateChange(fakePort::calls)
                .andVerify(calls -> {
                    assertThat(calls).hasSize(1);
                    assertThat(calls.get(0).serviceOrderId()).isEqualTo(serviceOrder.id());
                    assertThat(calls.get(0).customerId()).isEqualTo(customerId);
                });
    }

    @TestConfiguration
    static class FakePortConfiguration {

        @Bean
        @Primary
        FakeCustomerEstimateNotificationPort fakeCustomerEstimateNotificationPort() {
            return new FakeCustomerEstimateNotificationPort();
        }
    }

    static class FakeCustomerEstimateNotificationPort implements CustomerEstimateNotificationPort {

        private final List<Call> calls = new CopyOnWriteArrayList<>();

        @Override
        public void notifyEstimateGenerated(UUID estimateId, UUID serviceOrderId, UUID customerId, Instant expiresAt) {
            calls.add(new Call(estimateId, serviceOrderId, customerId, expiresAt));
        }

        List<Call> calls() {
            return calls;
        }

        record Call(UUID estimateId, UUID serviceOrderId, UUID customerId, Instant expiresAt) {
        }
    }
}
