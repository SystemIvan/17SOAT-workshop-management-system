package br.com.fiap.workshop_management_system.registration.vehicle.domain.model;

import java.util.Objects;

public final class VehicleYear {

    public static final int MINIMUM_YEAR = 1886;

    private final int value;

    private VehicleYear(int value) {
        this.value = value;
    }

    public static VehicleYear create(int value, int currentYear) {
        int maximumYear = currentYear + 1;
        if (value < MINIMUM_YEAR || value > maximumYear) {
            throw new IllegalArgumentException(
                    "O ano do veículo deve estar entre " + MINIMUM_YEAR + " e " + maximumYear);
        }
        return new VehicleYear(value);
    }

    public static VehicleYear reconstitute(int value) {
        if (value < MINIMUM_YEAR) {
            throw new IllegalArgumentException("O ano persistido do veículo é inválido");
        }
        return new VehicleYear(value);
    }

    public int value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof VehicleYear vehicleYear && value == vehicleYear.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }
}
