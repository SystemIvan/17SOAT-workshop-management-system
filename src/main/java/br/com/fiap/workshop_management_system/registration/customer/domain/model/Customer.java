package br.com.fiap.workshop_management_system.registration.customer.domain.model;

import java.util.UUID;

/**
 * Raiz do agregado e único ponto de entrada para alterar os dados de um Customer.
 */
public class Customer {

    private final UUID id;
    private final TaxId taxId;

    private String name;
    private ContactInfo contactInfo;
    private boolean active;

    public static Customer create(String name, TaxId taxId, ContactInfo contactInfo) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("O nome do cliente não pode estar em branco");
        }
        if (taxId == null) {
            throw new IllegalArgumentException("O CPF/CNPJ do cliente não pode ser nulo");
        }
        if (contactInfo == null) {
            throw new IllegalArgumentException("As informações de contato do cliente não podem ser nulas");
        }
        return new Customer(UUID.randomUUID(), name, taxId, contactInfo, true);
    }

    private Customer(UUID id, String name, TaxId taxId, ContactInfo contactInfo, boolean active) {
        this.id = id;
        this.name = name;
        this.taxId = taxId;
        this.contactInfo = contactInfo;
        this.active = active;
    }

    /**
     * Reconstrói um Customer a partir do estado persistido. Uso exclusivo do adapter
     * de persistência; diferentemente de {@link #create}, não executa as regras de criação.
     */
    public static Customer reconstitute(UUID id, String name, TaxId taxId, ContactInfo contactInfo, boolean active) {
        return new Customer(id, name, taxId, contactInfo, active);
    }

    public void rename(String newName) {
        ensureActive();
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("O nome do cliente não pode estar em branco");
        }
        this.name = newName;
    }

    public void updateContactInfo(Email email, Phone phone, Address address) {
        ensureActive();
        this.contactInfo = contactInfo.withUpdates(email, phone, address);
    }

    public void archive() {
        active = false;
    }

    private void ensureActive() {
        if (!active) {
            throw new CustomerArchivedException();
        }
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public TaxId taxId() {
        return taxId;
    }

    public ContactInfo contactInfo() {
        return contactInfo;
    }

    public boolean active() {
        return active;
    }
}
