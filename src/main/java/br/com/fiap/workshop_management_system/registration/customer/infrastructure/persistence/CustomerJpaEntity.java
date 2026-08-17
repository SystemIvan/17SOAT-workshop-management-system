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

    @Column(name = "address_street", length = 255)
    private String addressStreet;

    @Column(name = "address_number", length = 255)
    private String addressNumber;

    @Column(name = "address_complement", length = 255)
    private String addressComplement;

    @Column(name = "address_neighborhood", length = 255)
    private String addressNeighborhood;

    @Column(name = "address_city", length = 255)
    private String addressCity;

    @Column(name = "address_state", length = 2)
    private String addressState;

    @Column(name = "address_postal_code", length = 8)
    private String addressPostalCode;

    protected CustomerJpaEntity() {
    }

    public CustomerJpaEntity(
            UUID id,
            String name,
            String document,
            String contactEmail,
            String contactPhone,
            String addressStreet,
            String addressNumber,
            String addressComplement,
            String addressNeighborhood,
            String addressCity,
            String addressState,
            String addressPostalCode) {
        this.id = id;
        this.name = name;
        this.document = document;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.addressStreet = addressStreet;
        this.addressNumber = addressNumber;
        this.addressComplement = addressComplement;
        this.addressNeighborhood = addressNeighborhood;
        this.addressCity = addressCity;
        this.addressState = addressState;
        this.addressPostalCode = addressPostalCode;
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

    public String getAddressStreet() {
        return addressStreet;
    }

    public String getAddressNumber() {
        return addressNumber;
    }

    public String getAddressComplement() {
        return addressComplement;
    }

    public String getAddressNeighborhood() {
        return addressNeighborhood;
    }

    public String getAddressCity() {
        return addressCity;
    }

    public String getAddressState() {
        return addressState;
    }

    public String getAddressPostalCode() {
        return addressPostalCode;
    }
}
