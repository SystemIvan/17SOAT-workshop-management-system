package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RF39 - mapeamento total de ServiceOrderStatus (7 valores) para os 6 estados nominais da Fase 2.
 */
class ServiceOrderStatusLabelTest {

    static Stream<Arguments> statusToLabel() {
        return Stream.of(
                Arguments.of(ServiceOrderStatus.RECEIVED, ServiceOrderStatusLabel.RECEBIDA),
                Arguments.of(ServiceOrderStatus.IN_DIAGNOSIS, ServiceOrderStatusLabel.DIAGNOSTICO),
                Arguments.of(ServiceOrderStatus.AWAITING_APPROVAL, ServiceOrderStatusLabel.AGUARDANDO_APROVACAO),
                Arguments.of(ServiceOrderStatus.IN_PROGRESS, ServiceOrderStatusLabel.EXECUCAO),
                Arguments.of(ServiceOrderStatus.AWAITING_ITEMS, ServiceOrderStatusLabel.EXECUCAO),
                Arguments.of(ServiceOrderStatus.COMPLETED, ServiceOrderStatusLabel.FINALIZADA),
                Arguments.of(ServiceOrderStatus.DELIVERED, ServiceOrderStatusLabel.ENTREGUE));
    }

    @ParameterizedTest
    @MethodSource("statusToLabel")
    void mapsEveryInternalStatusToItsNominalLabel(ServiceOrderStatus status, ServiceOrderStatusLabel expected) {
        assertEquals(expected, ServiceOrderStatusLabel.from(status));
    }

    @Test
    void awaitingItemsAndInProgressShareTheSameNominalLabel() {
        assertEquals(
                ServiceOrderStatusLabel.from(ServiceOrderStatus.IN_PROGRESS),
                ServiceOrderStatusLabel.from(ServiceOrderStatus.AWAITING_ITEMS));
    }
}
