package br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model;

import java.util.UUID;

public class CatalogService {

    private final UUID id;
    private CatalogServiceName name;
    private Money basePrice;
    private boolean active;

    public static CatalogService create(CatalogServiceName name, Money basePrice) {
        return new CatalogService(UUID.randomUUID(), name, basePrice, true);
    }

    public static CatalogService reconstitute(
            UUID id,
            CatalogServiceName name,
            Money basePrice,
            boolean active) {
        return new CatalogService(id, name, basePrice, active);
    }

    private CatalogService(UUID id, CatalogServiceName name, Money basePrice, boolean active) {
        if (id == null) {
            throw new IllegalArgumentException("O identificador do serviço é obrigatório");
        }
        if (name == null) {
            throw new IllegalArgumentException("O nome do serviço é obrigatório");
        }
        if (basePrice == null) {
            throw new IllegalArgumentException("O preço-base do serviço é obrigatório");
        }
        this.id = id;
        this.name = name;
        this.basePrice = basePrice;
        this.active = active;
    }

    public UUID id() {
        return id;
    }

    public CatalogServiceName name() {
        return name;
    }

    public Money basePrice() {
        return basePrice;
    }

    public boolean active() {
        return active;
    }

    public boolean rename(CatalogServiceName newName) {
        ensureActive();
        if (newName == null) {
            throw new IllegalArgumentException("O nome do serviço é obrigatório");
        }
        if (name.value().equals(newName.value())) {
            return false;
        }

        name = newName;
        return true;
    }

    public boolean updateBasePrice(Money newBasePrice) {
        ensureActive();
        if (newBasePrice == null) {
            throw new IllegalArgumentException("O preço-base do serviço é obrigatório");
        }
        if (basePrice.equals(newBasePrice)) {
            return false;
        }

        basePrice = newBasePrice;
        return true;
    }

    public boolean archive() {
        if (!active) {
            return false;
        }

        active = false;
        return true;
    }

    private void ensureActive() {
        if (!active) {
            throw new CatalogServiceArchivedException();
        }
    }
}
