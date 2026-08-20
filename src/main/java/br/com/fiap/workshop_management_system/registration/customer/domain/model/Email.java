package br.com.fiap.workshop_management_system.registration.customer.domain.model;

import java.util.regex.Pattern;

public record Email(String value) {

    private static final int MAX_LENGTH = 254;
    private static final Pattern VALID_FORMAT = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public Email {
        if (value == null) {
            throw new IllegalArgumentException("O e-mail de contato deve ser informado");
        }
        value = value.strip();
        if (value.isEmpty() || value.length() > MAX_LENGTH || !VALID_FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("O e-mail de contato deve ser válido");
        }
    }
}
