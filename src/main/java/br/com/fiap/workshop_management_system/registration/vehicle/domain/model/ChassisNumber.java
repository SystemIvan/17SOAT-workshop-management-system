package br.com.fiap.workshop_management_system.registration.vehicle.domain.model;

import java.util.Locale;
import java.util.regex.Pattern;

public record ChassisNumber(String value) {

    private static final Pattern CHASSIS_PATTERN = Pattern.compile("[A-Z0-9]{17}");

    public ChassisNumber {
        if (value == null) {
            throw new IllegalArgumentException("O chassis do veículo não pode ser nulo");
        }

        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (!CHASSIS_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("O chassis do veículo deve possuir 17 caracteres alfanuméricos");
        }
        value = normalized;
    }
}
