package br.com.fiap.workshop_management_system.registration.customer.domain.model;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public record Address(
        String street,
        String number,
        String complement,
        String neighborhood,
        String city,
        String state,
        String postalCode) {

    private static final int MAX_TEXT_LENGTH = 255;
    private static final Pattern POSTAL_CODE_FORMAT = Pattern.compile("\\d{5}-?\\d{3}");
    private static final Set<String> BRAZILIAN_STATES = Set.of(
            "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA",
            "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

    public Address {
        street = normalizeRequired(street, "logradouro");
        number = normalizeRequired(number, "número");
        complement = normalizeOptional(complement, "complemento");
        neighborhood = normalizeOptional(neighborhood, "bairro");
        city = normalizeRequired(city, "cidade");
        state = normalizeState(state);
        postalCode = normalizePostalCode(postalCode);
    }

    private static String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("O campo " + fieldName + " do endereço deve ser informado");
        }
        return normalizeLength(value, fieldName);
    }

    private static String normalizeOptional(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return normalizeLength(value, fieldName);
    }

    private static String normalizeLength(String value, String fieldName) {
        String normalizedValue = value.strip();
        if (normalizedValue.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("O campo " + fieldName + " do endereço deve ter até 255 caracteres");
        }
        return normalizedValue;
    }

    private static String normalizeState(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A UF do endereço deve ser informada");
        }
        String normalizedValue = value.strip().toUpperCase(Locale.ROOT);
        if (!BRAZILIAN_STATES.contains(normalizedValue)) {
            throw new IllegalArgumentException("A UF do endereço deve ser uma sigla brasileira válida");
        }
        return normalizedValue;
    }

    private static String normalizePostalCode(String value) {
        if (value == null || !POSTAL_CODE_FORMAT.matcher(value.strip()).matches()) {
            throw new IllegalArgumentException("O CEP do endereço deve conter oito dígitos");
        }
        return value.strip().replace("-", "");
    }
}
