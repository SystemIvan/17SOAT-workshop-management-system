package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.dto.StockReservationResponse;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.exception.StockReservationNotFoundException;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.domain.model.StockReservation;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.domain.model.StockReservationLine;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.domain.repository.StockReservationRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetStockReservationUseCaseTest {

    @Test
    void getsAReservationByItsIdAndServiceExecutionId() {
        StockReservation reservation = StockReservation.create(
                UUID.randomUUID(),
                List.of(new StockReservationLine(UUID.randomUUID(), 2)),
                Instant.parse("2026-08-21T12:00:00Z"));
        InMemoryStockReservationRepository repository = new InMemoryStockReservationRepository(reservation);

        StockReservationResponse byId = new GetStockReservationUseCase(repository).execute(reservation.id());
        StockReservationResponse byExecution = new GetStockReservationByExecutionUseCase(repository)
                .execute(reservation.serviceExecutionId());

        assertEquals(reservation.id(), byId.id());
        assertEquals(reservation.serviceExecutionId(), byExecution.serviceExecutionId());
        assertEquals(2, byExecution.lines().getFirst().quantity());
    }

    @Test
    void rejectsUnknownReservations() {
        InMemoryStockReservationRepository repository = new InMemoryStockReservationRepository();

        assertThrows(StockReservationNotFoundException.class,
                () -> new GetStockReservationUseCase(repository).execute(UUID.randomUUID()));
        assertThrows(StockReservationNotFoundException.class,
                () -> new GetStockReservationByExecutionUseCase(repository).execute(UUID.randomUUID()));
    }

    private static final class InMemoryStockReservationRepository implements StockReservationRepository {

        private final Map<UUID, StockReservation> byId = new HashMap<>();

        private InMemoryStockReservationRepository(StockReservation... reservations) {
            for (StockReservation reservation : reservations) {
                byId.put(reservation.id(), reservation);
            }
        }

        @Override
        public Optional<StockReservation> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<StockReservation> findByIdForUpdate(UUID id) {
            return findById(id);
        }

        @Override
        public Optional<StockReservation> findByServiceExecutionId(UUID serviceExecutionId) {
            return byId.values().stream()
                    .filter(reservation -> reservation.serviceExecutionId().equals(serviceExecutionId))
                    .findFirst();
        }

        @Override
        public List<StockReservation> findByServiceExecutionIdIn(Collection<UUID> serviceExecutionIds) {
            return byId.values().stream()
                    .filter(reservation -> serviceExecutionIds.contains(reservation.serviceExecutionId()))
                    .toList();
        }

        @Override
        public void save(StockReservation reservation) {
            byId.put(reservation.id(), reservation);
        }
    }
}
