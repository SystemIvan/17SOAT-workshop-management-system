package br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.dto;

public record VehicleSnapshotResponse(
        String licensePlate,
        String brand,
        String model,
        int year) {
}
