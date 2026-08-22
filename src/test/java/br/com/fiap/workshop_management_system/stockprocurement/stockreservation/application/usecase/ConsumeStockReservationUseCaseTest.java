package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.domain.model.StockReservation;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.domain.model.StockReservationLine;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.domain.repository.StockReservationRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConsumeStockReservationUseCaseTest {

    @Test
    void consumesAnActiveReservationIdempotentlyWithTheInjectedClock() {
        InMemoryRepository repository = new InMemoryRepository();
        StockReservation reservation = StockReservation.create(
                UUID.randomUUID(),
                List.of(new StockReservationLine(UUID.randomUUID(), 1)),
                Instant.parse("2026-08-20T11:00:00Z"));
        repository.save(reservation);
        Instant consumedAt = Instant.parse("2026-08-20T12:00:00Z");
        ConsumeStockReservationUseCase useCase = new ConsumeStockReservationUseCase(
                repository,
                Clock.fixed(consumedAt, ZoneOffset.UTC));

        useCase.execute(reservation.id());
        var repeated = useCase.execute(reservation.id());

        assertEquals(consumedAt, repeated.consumedAt());
    }

    private static final class InMemoryRepository implements StockReservationRepository {
        private final Map<UUID, StockReservation> reservations = new HashMap<>();

        @Override
        public Optional<StockReservation> findById(UUID id) {
            return Optional.ofNullable(reservations.get(id));
        }

        @Override
        public Optional<StockReservation> findByIdForUpdate(UUID id) {
            return findById(id);
        }

        @Override
        public Optional<StockReservation> findByServiceExecutionId(UUID serviceExecutionId) {
            return reservations.values().stream()
                    .filter(reservation -> reservation.serviceExecutionId().equals(serviceExecutionId))
                    .findFirst();
        }

        @Override
        public List<StockReservation> findByServiceExecutionIdIn(Collection<UUID> serviceExecutionIds) {
            return reservations.values().stream()
                    .filter(reservation -> serviceExecutionIds.contains(reservation.serviceExecutionId()))
                    .toList();
        }

        @Override
        public void save(StockReservation reservation) {
            reservations.put(reservation.id(), reservation);
        }
    }
}
