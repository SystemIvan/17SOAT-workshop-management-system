package br.com.fiap.workshop_management_system.registration.customer.domain.model;

public record ContactInfo(String email, String phone) {

    public ContactInfo {
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("O e-mail de contato deve ser válido");
        }
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("O telefone de contato não pode estar em branco");
        }
    }
}
