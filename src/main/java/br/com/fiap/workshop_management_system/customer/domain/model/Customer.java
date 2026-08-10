package br.com.fiap.workshop_management_system.customer.domain.model;

import java.util.UUID;

/**
 * Aggregate Root. The only entry point for changing a Customer's data.
 */
public class Customer {

    private final UUID id;
    private final String document;

    private String name;
    private ContactInfo contactInfo;

    public static Customer create(String name, String document, ContactInfo contactInfo) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Customer name must not be blank");
        }
        if (document == null || document.isBlank()) {
            throw new IllegalArgumentException("Customer document must not be blank");
        }
        if (contactInfo == null) {
            throw new IllegalArgumentException("Customer contact info must not be null");
        }
        return new Customer(UUID.randomUUID(), name, document, contactInfo);
    }

    private Customer(UUID id, String name, String document, ContactInfo contactInfo) {
        this.id = id;
        this.name = name;
        this.document = document;
        this.contactInfo = contactInfo;
    }

    /**
     * Rebuilds a Customer from previously persisted state. Used exclusively by the
     * persistence adapter - unlike {@link #create}, it does not run creation rules.
     */
    public static Customer reconstitute(UUID id, String name, String document, ContactInfo contactInfo) {
        return new Customer(id, name, document, contactInfo);
    }

    public void rename(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Customer name must not be blank");
        }
        this.name = newName;
    }

    public void updateContactInfo(ContactInfo newContactInfo) {
        if (newContactInfo == null) {
            throw new IllegalArgumentException("Customer contact info must not be null");
        }
        this.contactInfo = newContactInfo;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String document() {
        return document;
    }

    public ContactInfo contactInfo() {
        return contactInfo;
    }
}
