package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.infrastructure.persistence;

import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.domain.model.StockReservationStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "stock_reservations")
public class StockReservationJpaEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "service_execution_id", nullable = false, unique = true)
    private UUID serviceExecutionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StockReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @ElementCollection
    @CollectionTable(name = "stock_reservation_lines", joinColumns = @JoinColumn(name = "reservation_id"))
    private List<StockReservationLineEmbeddable> lines = new ArrayList<>();

    protected StockReservationJpaEntity() {
    }

    public StockReservationJpaEntity(
            UUID id,
            UUID serviceExecutionId,
            StockReservationStatus status,
            Instant createdAt,
            Instant consumedAt,
            List<StockReservationLineEmbeddable> lines) {
        this.id = id;
        this.serviceExecutionId = serviceExecutionId;
        this.status = status;
        this.createdAt = createdAt;
        this.consumedAt = consumedAt;
        this.lines = lines;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return false;
    }

    public UUID getServiceExecutionId() {
        return serviceExecutionId;
    }

    public StockReservationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public List<StockReservationLineEmbeddable> getLines() {
        return lines;
    }
}
