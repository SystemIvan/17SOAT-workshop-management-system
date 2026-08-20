package br.com.fiap.workshop_management_system.servicelifecycle.estimate.infrastructure.persistence;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "estimate_lines")
public class EstimateLineJpaEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estimate_id", nullable = false)
    private EstimateJpaEntity estimate;

    @Column(name = "line_order", nullable = false)
    private int lineOrder;

    @Column(name = "service_execution_id", nullable = false)
    private UUID serviceExecutionId;

    @Column(name = "service_name", nullable = false, length = 255)
    private String serviceName;

    @Column(name = "service_price_value", nullable = false, precision = 38, scale = 2)
    private BigDecimal servicePriceValue;

    @Column(name = "service_price_currency", nullable = false, length = 3)
    private String servicePriceCurrency;

    @OneToMany(
            mappedBy = "estimateLine",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<EstimateStockItemJpaEntity> stockItems = new ArrayList<>();

    protected EstimateLineJpaEntity() {
    }

    public EstimateLineJpaEntity(
            UUID id,
            int lineOrder,
            UUID serviceExecutionId,
            String serviceName,
            BigDecimal servicePriceValue,
            String servicePriceCurrency) {
        this.id = id;
        this.lineOrder = lineOrder;
        this.serviceExecutionId = serviceExecutionId;
        this.serviceName = serviceName;
        this.servicePriceValue = servicePriceValue;
        this.servicePriceCurrency = servicePriceCurrency;
    }

    void setEstimate(EstimateJpaEntity estimate) {
        this.estimate = estimate;
    }

    public void addStockItem(EstimateStockItemJpaEntity stockItem) {
        stockItems.add(stockItem);
        stockItem.setEstimateLine(this);
    }

    public UUID getId() { return id; }
    public int getLineOrder() { return lineOrder; }
    public UUID getServiceExecutionId() { return serviceExecutionId; }
    public String getServiceName() { return serviceName; }
    public BigDecimal getServicePriceValue() { return servicePriceValue; }
    public String getServicePriceCurrency() { return servicePriceCurrency; }
    public List<EstimateStockItemJpaEntity> getStockItems() { return stockItems; }
}