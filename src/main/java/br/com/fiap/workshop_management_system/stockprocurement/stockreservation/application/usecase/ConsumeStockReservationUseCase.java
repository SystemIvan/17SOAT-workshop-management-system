package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.dto.StockReservationMapper;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.dto.StockReservationResponse;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.exception.StockReservationNotFoundException;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.domain.repository.StockReservationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class ConsumeStockReservationUseCase {

    private final StockReservationRepository repository;
    private final Clock clock;

    @Autowired
    public ConsumeStockReservationUseCase(StockReservationRepository repository) {
        this(repository, Clock.systemUTC());
    }

    ConsumeStockReservationUseCase(StockReservationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public StockReservationResponse execute(UUID reservationId) {
        var reservation = repository.findByIdForUpdate(reservationId)
                .orElseThrow(StockReservationNotFoundException::new);
        reservation.consume(currentTime());
        repository.save(reservation);
        return StockReservationMapper.toResponse(reservation);
    }

    private Instant currentTime() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
