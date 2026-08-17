package br.com.fiap.workshop_management_system.registration.vehicle.domain.model;

public class VehicleArchivedException extends RuntimeException {

    public VehicleArchivedException() {
        super("Veículo arquivado não pode ser atualizado");
    }
}
