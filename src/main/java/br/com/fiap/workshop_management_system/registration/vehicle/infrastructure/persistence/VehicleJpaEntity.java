package br.com.fiap.workshop_management_system.registration.vehicle.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

import java.util.UUID;

@Entity
@Table(name = "vehicles")
public class VehicleJpaEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "license_plate", nullable = false, unique = true, length = 7)
    private String licensePlate;

    @Column(name = "chassis_number", unique = true, length = 17)
    private String chassisNumber;

    @Column(nullable = false, length = 100)
    private String brand;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "model_year", nullable = false)
    private int modelYear;

    @Column(nullable = false, length = 50)
    private String color;

    @Column
    private Long mileage;

    @Column(nullable = false)
    private boolean active;

    protected VehicleJpaEntity() {
    }

    public VehicleJpaEntity(
            UUID id,
            UUID customerId,
            String licensePlate,
            String chassisNumber,
            String brand,
            String model,
            int modelYear,
            String color,
            Long mileage,
            boolean active) {
        this.id = id;
        this.customerId = customerId;
        this.licensePlate = licensePlate;
        this.chassisNumber = chassisNumber;
        this.brand = brand;
        this.model = model;
        this.modelYear = modelYear;
        this.color = color;
        this.mileage = mileage;
        this.active = active;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return false;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public String getChassisNumber() {
        return chassisNumber;
    }

    public String getBrand() {
        return brand;
    }

    public String getModel() {
        return model;
    }

    public int getModelYear() {
        return modelYear;
    }

    public String getColor() {
        return color;
    }

    public Long getMileage() {
        return mileage;
    }

    public boolean isActive() {
        return active;
    }
}
