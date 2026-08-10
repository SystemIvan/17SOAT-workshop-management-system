package br.com.fiap.workshop_management_system.serviceorder.application.dto;

public record VehicleSnapshotResponse(
        String licensePlate,
        String brand,
        String model,
        int year) {
}
