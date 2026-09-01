package br.com.fiap.workshop_management_system.registration.servicecatalog.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "catalog_services")
public class CatalogServiceJpaEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "normalized_name_key", nullable = false, length = 1020)
    private byte[] normalizedNameKey;

    @Column(name = "base_price_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal basePriceValue;

    @Column(name = "base_price_currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String basePriceCurrency;

    @Column(nullable = false)
    private boolean active;

    protected CatalogServiceJpaEntity() {
    }

    public CatalogServiceJpaEntity(
            UUID id,
            String name,
            byte[] normalizedNameKey,
            BigDecimal basePriceValue,
            String basePriceCurrency,
            boolean active) {
        this.id = id;
        this.name = name;
        this.normalizedNameKey = normalizedNameKey.clone();
        this.basePriceValue = basePriceValue;
        this.basePriceCurrency = basePriceCurrency;
        this.active = active;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return false;
    }

    public String getName() {
        return name;
    }

    public byte[] getNormalizedNameKey() {
        return normalizedNameKey.clone();
    }

    public BigDecimal getBasePriceValue() {
        return basePriceValue;
    }

    public String getBasePriceCurrency() {
        return basePriceCurrency;
    }

    public boolean isActive() {
        return active;
    }
}
