package br.com.fiap.workshop_management_system.registration.vehicle.domain.model;

public record Mileage(long value) implements Comparable<Mileage> {

    public Mileage {
        if (value < 0) {
            throw new IllegalArgumentException("A quilometragem do veículo não pode ser negativa");
        }
    }

    @Override
    public int compareTo(Mileage other) {
        return Long.compare(value, other.value);
    }
}
