package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.dto.StockReservationMapper;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.dto.StockReservationResponse;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.exception.StockReservationNotFoundException;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.domain.repository.StockReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetStockReservationUseCase {

    private final StockReservationRepository repository;

    public GetStockReservationUseCase(StockReservationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public StockReservationResponse execute(UUID reservationId) {
        return repository.findById(reservationId)
                .map(StockReservationMapper::toResponse)
                .orElseThrow(StockReservationNotFoundException::new);
    }
}
