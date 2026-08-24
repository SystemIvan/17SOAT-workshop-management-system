package br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model;

import java.util.Locale;

public record CatalogServiceName(String value) {

    private static final int MAX_LENGTH = 255;

    public CatalogServiceName {
        if (value == null) {
            throw new IllegalArgumentException("O nome do serviço é obrigatório");
        }

        value = value.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("O nome do serviço não pode estar vazio");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("O nome do serviço deve ter no máximo 255 caracteres");
        }
    }

    public String canonicalValue() {
        return value.toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof CatalogServiceName otherName
                && canonicalValue().equals(otherName.canonicalValue());
    }

    @Override
    public int hashCode() {
        return canonicalValue().hashCode();
    }
}
