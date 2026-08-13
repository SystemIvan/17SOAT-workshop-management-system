package br.com.fiap.workshop_management_system.registration.customer.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

import java.util.UUID;

/**
 * JPA projection of the {@link br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer}
 * aggregate. Kept separate from the domain class so the domain stays framework-agnostic.
 *
 * <p>Implements {@link Persistable} because the id (UUID) is always assigned by the domain
 * before persistence - without this, Spring Data would assume every save() is an update.
 */
@Entity
@Table(name = "customers")
public class CustomerJpaEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    private String name;
    private String document;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    protected CustomerJpaEntity() {
    }

    public CustomerJpaEntity(UUID id, String name, String document, String contactEmail, String contactPhone) {
        this.id = id;
        this.name = name;
        this.document = document;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
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

    public String getDocument() {
        return document;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }
}
