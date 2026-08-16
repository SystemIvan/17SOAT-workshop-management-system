package br.com.fiap.workshop_management_system.servicelifecycle.estimate.infrastructure.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "estimates",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_estimates_diagnosis_id",
                columnNames = "diagnosis_id"
        )
)
public class EstimateJpaEntity {

    @Id
    private UUID id;

    @Column(name = "service_order_id", nullable = false)
    private UUID serviceOrderId;

    @Column(name = "diagnosis_id", nullable = false)
    private UUID diagnosisId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @OneToMany(
            mappedBy = "estimate",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderColumn(name = "line_order")
    private List<EstimateLineJpaEntity> lines = new ArrayList<>();

    protected EstimateJpaEntity() {
    }

    public EstimateJpaEntity(
            UUID id,
            UUID serviceOrderId,
            UUID diagnosisId,
            UUID customerId,
            Instant createdAt,
            Instant expiresAt) {
        this.id = id;
        this.serviceOrderId = serviceOrderId;
        this.diagnosisId = diagnosisId;
        this.customerId = customerId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public void addLine(EstimateLineJpaEntity line) {
        lines.add(line);
        line.setEstimate(this);
    }

    public UUID getId() { return id; }
    public UUID getServiceOrderId() { return serviceOrderId; }
    public UUID getDiagnosisId() { return diagnosisId; }
    public UUID getCustomerId() { return customerId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public List<EstimateLineJpaEntity> getLines() { return lines; }
}