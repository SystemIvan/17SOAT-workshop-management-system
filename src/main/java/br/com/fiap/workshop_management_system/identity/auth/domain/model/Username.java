package br.com.fiap.workshop_management_system.identity.auth.domain.model;

public record Username(String value) {

    private static final int MAX_LENGTH = 255;

    public Username {
        if (value == null) {
            throw new IllegalArgumentException("Username must not be null");
        }
        value = value.strip();
        if (value.isEmpty() || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Username must not be blank");
        }
    }
}
