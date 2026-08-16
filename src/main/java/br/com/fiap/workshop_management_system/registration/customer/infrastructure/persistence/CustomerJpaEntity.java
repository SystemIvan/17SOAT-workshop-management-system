package br.com.fiap.workshop_management_system.registration.customer.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

import java.util.UUID;

/**
 * Projeção JPA do agregado
 * {@link br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer}.
 * Permanece separada da classe de domínio para que o domínio não dependa de frameworks.
 *
 * <p>Implementa {@link Persistable} para declarar explicitamente a estratégia de merge
 * usada quando o domínio atribui o ID (UUID) antes da persistência.
 */
@Entity
@Table(name = "customers")
public class CustomerJpaEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, unique = true, length = 14)
    private String document;

    @Column(name = "contact_email", nullable = false, length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", nullable = false, length = 255)
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
