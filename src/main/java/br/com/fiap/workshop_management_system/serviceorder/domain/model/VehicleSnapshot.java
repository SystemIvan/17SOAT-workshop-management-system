package br.com.fiap.workshop_management_system.serviceorder.domain.model;

/**
 * Frozen at ServiceOrder creation time - never updated by later changes to the Vehicle.
 */
public record VehicleSnapshot(String licensePlate, String brand, String model, int year) {
}