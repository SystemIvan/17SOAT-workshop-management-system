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

    private ChassisNumber chassisNumber;
    private String brand;
    private String model;
    private VehicleYear year;
    private String color;
    private Mileage mileage;
    private boolean active;

    public static Vehicle create(
            UUID customerId,
            LicensePlate licensePlate,
            ChassisNumber chassisNumber,
            String brand,
            String model,
            VehicleYear year,
            String color,
            Mileage mileage) {
        return new Vehicle(UUID.randomUUID(), customerId, licensePlate, chassisNumber,
                brand, model, year, color, mileage, true);
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
            Mileage mileage,
            boolean active) {
        return new Vehicle(id, customerId, licensePlate, chassisNumber,
                brand, model, year, color, mileage, active);
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
            Mileage mileage,
            boolean active) {
        this.id = Objects.requireNonNull(id, "O ID do veículo é obrigatório");
        this.customerId = Objects.requireNonNull(customerId, "O cliente do veículo é obrigatório");
        this.licensePlate = Objects.requireNonNull(licensePlate, "A placa do veículo é obrigatória");
        this.chassisNumber = chassisNumber;
        this.brand = normalizeRequired(brand, "A marca do veículo", MAX_BRAND_LENGTH);
        this.model = normalizeRequired(model, "O modelo do veículo", MAX_MODEL_LENGTH);
        this.year = Objects.requireNonNull(year, "O ano do veículo é obrigatório");
        this.color = normalizeRequired(color, "A cor do veículo", MAX_COLOR_LENGTH);
        this.mileage = mileage;
        this.active = active;
    }

    public void updateDetails(
            String brand,
            String model,
            VehicleYear year,
            String color,
            ChassisNumber chassisUpdate) {
        if (!active) {
            throw new VehicleArchivedException();
        }

        String normalizedBrand = normalizeRequired(brand, "A marca do veículo", MAX_BRAND_LENGTH);
        String normalizedModel = normalizeRequired(model, "O modelo do veículo", MAX_MODEL_LENGTH);
        VehicleYear validatedYear = Objects.requireNonNull(year, "O ano do veículo é obrigatório");
        String normalizedColor = normalizeRequired(color, "A cor do veículo", MAX_COLOR_LENGTH);

        this.brand = normalizedBrand;
        this.model = normalizedModel;
        this.year = validatedYear;
        this.color = normalizedColor;
        if (chassisUpdate != null) {
            this.chassisNumber = chassisUpdate;
        }
    }

    public boolean recordMileage(Mileage newMileage) {
        Objects.requireNonNull(newMileage, "A quilometragem do veículo é obrigatória");
        if (!active) {
            throw new VehicleArchivedException();
        }
        if (mileage != null && newMileage.compareTo(mileage) < 0) {
            throw new VehicleMileageCannotDecreaseException();
        }
        if (newMileage.equals(mileage)) {
            return false;
        }
        mileage = newMileage;
        return true;
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

    public Optional<Mileage> mileage() {
        return Optional.ofNullable(mileage);
    }

    public boolean active() {
        return active;
    }
}
