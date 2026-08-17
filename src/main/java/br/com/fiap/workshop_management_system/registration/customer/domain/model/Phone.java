package br.com.fiap.workshop_management_system.registration.customer.domain.model;

import java.util.regex.Pattern;

public record Phone(String value) {

    private static final Pattern ACCEPTED_CHARACTERS = Pattern.compile("[+0-9()\\-\\s]+");
    private static final Pattern E164_DIGITS = Pattern.compile("[1-9]\\d{7,14}");

    public Phone {
        value = normalize(value);
    }

    private static String normalize(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw invalidPhone();
        }

        String input = rawValue.strip();
        if (!ACCEPTED_CHARACTERS.matcher(input).matches()) {
            throw invalidPhone();
        }

        boolean hasCountryPrefix = input.startsWith("+");
        if (input.indexOf('+') != input.lastIndexOf('+') || (!hasCountryPrefix && input.contains("+"))) {
            throw invalidPhone();
        }

        String digits = input.replaceAll("\\D", "");
        String canonicalValue;
        if (hasCountryPrefix) {
            canonicalValue = "+" + digits;
        } else if (digits.length() == 10 || digits.length() == 11) {
            canonicalValue = "+55" + digits;
        } else {
            throw invalidPhone();
        }

        if (!E164_DIGITS.matcher(canonicalValue.substring(1)).matches()) {
            throw invalidPhone();
        }
        return canonicalValue;
    }

    private static IllegalArgumentException invalidPhone() {
        return new IllegalArgumentException("O telefone de contato deve ser brasileiro ou estar no formato E.164");
    }
}
