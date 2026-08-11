package br.com.fiap.workshop_management_system.registration.customer.domain.model;

public record ContactInfo(String email, String phone) {

    public ContactInfo {
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("ContactInfo email must be a valid address");
        }
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("ContactInfo phone must not be blank");
        }
    }
}
