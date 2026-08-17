package br.com.fiap.workshop_management_system.registration.vehicle.domain.model;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class Vehicle {

    public static final int MAX_BRAND_LENGTH = 100;
    public static final int MAX_MODEL_LENGTH = 100;
    public static final int MAX_COLOR_LENGTH = 50;

    private final UUID id;
    private final UUID customerId;
    private final LicensePlate licensePlate;
    private final ChassisNumber chassisNumber;
    private final VehicleYear year;

    private String brand;
    private String model;
    private String color;
    private boolean active;

    public static Vehicle create(
            UUID customerId,
            LicensePlate licensePlate,
            ChassisNumber chassisNumber,
            String brand,
            String model,
            VehicleYear year,
            String color) {
        return new Vehicle(UUID.randomUUID(), customerId, licensePlate, chassisNumber, brand, model, year, color, true);
    }

    public static Vehicle reconstitute(
            UUID id,
            UUID customerId,
            LicensePlate licensePlate,
            ChassisNumber chassisNumber,
            String brand,
            String model,
            VehicleYear year,
            String color,
            boolean active) {
        return new Vehicle(id, customerId, licensePlate, chassisNumber, brand, model, year, color, active);
    }

    private Vehicle(
            UUID id,
            UUID customerId,
            LicensePlate licensePlate,
            ChassisNumber chassisNumber,
            String brand,
            String model,
            VehicleYear year,
            String color,
            boolean active) {
        this.id = Objects.requireNonNull(id, "O ID do veículo é obrigatório");
        this.customerId = Objects.requireNonNull(customerId, "O cliente do veículo é obrigatório");
        this.licensePlate = Objects.requireNonNull(licensePlate, "A placa do veículo é obrigatória");
        this.chassisNumber = chassisNumber;
        this.brand = normalizeRequired(brand, "A marca do veículo", MAX_BRAND_LENGTH);
        this.model = normalizeRequired(model, "O modelo do veículo", MAX_MODEL_LENGTH);
        this.year = Objects.requireNonNull(year, "O ano do veículo é obrigatório");
        this.color = normalizeRequired(color, "A cor do veículo", MAX_COLOR_LENGTH);
        this.active = active;
    }

    private static String normalizeRequired(String value, String fieldName, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " é obrigatória");
        }
        String normalized = value.strip();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(fieldName + " deve possuir no máximo " + maximumLength + " caracteres");
        }
        return normalized;
    }

    public UUID id() {
        return id;
    }

    public UUID customerId() {
        return customerId;
    }

    public LicensePlate licensePlate() {
        return licensePlate;
    }

    public Optional<ChassisNumber> chassisNumber() {
        return Optional.ofNullable(chassisNumber);
    }

    public String brand() {
        return brand;
    }

    public String model() {
        return model;
    }

    public VehicleYear year() {
        return year;
    }

    public String color() {
        return color;
    }

    public boolean active() {
        return active;
    }
}
