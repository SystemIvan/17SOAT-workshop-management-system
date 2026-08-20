package br.com.fiap.workshop_management_system.registration.vehicle.domain.model;

import java.util.Locale;
import java.util.regex.Pattern;

public record LicensePlate(String value) {

    private static final Pattern LEGACY_PATTERN = Pattern.compile("[A-Z]{3}[0-9]{4}");
    private static final Pattern FORMATTED_LEGACY_PATTERN = Pattern.compile("[A-Z]{3}-[0-9]{4}");
    private static final Pattern MERCOSUL_PATTERN = Pattern.compile("[A-Z]{3}[0-9][A-Z][0-9]{2}");

    public LicensePlate {
        if (value == null) {
            throw new IllegalArgumentException("A placa do veículo é obrigatória");
        }

        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (FORMATTED_LEGACY_PATTERN.matcher(normalized).matches()) {
            normalized = normalized.replace("-", "");
        }
        if (!LEGACY_PATTERN.matcher(normalized).matches()
                && !MERCOSUL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("A placa do veículo possui formato inválido");
        }
        value = normalized;
    }
}
