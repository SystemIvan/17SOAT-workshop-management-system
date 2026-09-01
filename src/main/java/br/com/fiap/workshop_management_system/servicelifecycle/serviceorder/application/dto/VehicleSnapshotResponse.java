package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto;

public record VehicleSnapshotResponse(
        String licensePlate,
        String brand,
        String model,
        int year) {
}
