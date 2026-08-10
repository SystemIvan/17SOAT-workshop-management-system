package br.com.fiap.workshop_management_system.parts.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * JPA projection of the {@link br.com.fiap.workshop_management_system.parts.domain.model.Part}
 * aggregate. Kept separate from the domain class so the domain stays framework-agnostic.
 *
 * <p>Implements {@link Persistable} because the id (UUID) is always assigned by the domain
 * before persistence - without this, Spring Data would assume every save() is an update.
 */
@Entity
@Table(name = "parts")
public class PartJpaEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    private String name;
    private String sku;
    private int quantity;

    @Column(name = "price_value")
    private BigDecimal priceValue;

    protected PartJpaEntity() {
    }

    public PartJpaEntity(UUID id, String name, String sku, int quantity, BigDecimal priceValue) {
        this.id = id;
        this.name = name;
        this.sku = sku;
        this.quantity = quantity;
        this.priceValue = priceValue;
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

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getPriceValue() {
        return priceValue;
    }
}
