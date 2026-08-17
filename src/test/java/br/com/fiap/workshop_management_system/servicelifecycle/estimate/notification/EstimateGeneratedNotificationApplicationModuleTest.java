package br.com.fiap.workshop_management_system.servicelifecycle.estimate.notification;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.notification.application.port.CustomerEstimateNotificationPort;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.notification.event.EstimateGeneratedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.EnableScenarios;
import org.springframework.modulith.test.Scenario;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the real servicelifecycle + registration Spring context (no mocks for the wiring itself) to
 * prove that publishing an EstimateGeneratedEvent through the real ApplicationEventPublisher actually
 * reaches EstimateGeneratedNotificationListener via @ApplicationModuleListener - the architectural
 * decision recorded in technical-spec.md, not just the listener's own business logic (already covered
 * by EstimateGeneratedNotificationListenerTest).
 */
@ApplicationModuleTest(ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
@EnableScenarios
class EstimateGeneratedNotificationApplicationModuleTest {

    @Autowired
    private FakeCustomerEstimateNotificationPort fakePort;

    @Test
    void publishingAnEstimateGeneratedEventReachesTheListenerAndInvokesThePort(Scenario scenario) {
        UUID estimateId = UUID.randomUUID();
        UUID serviceOrderId = UUID.randomUUID();
        UUID diagnosisId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
        EstimateGeneratedEvent event = new EstimateGeneratedEvent(
                UUID.randomUUID(), Instant.now(), estimateId, serviceOrderId, diagnosisId, customerId, expiresAt);

        scenario.publish(event)
                .andWaitForStateChange(fakePort::calls)
                .andVerify(calls -> assertThat(calls).containsExactly(
                        new FakeCustomerEstimateNotificationPort.Call(estimateId, serviceOrderId, customerId, expiresAt)));
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
