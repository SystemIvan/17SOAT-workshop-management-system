package br.com.fiap.workshop_management_system.registration.vehicle.domain.model;

public class VehicleMileageCannotDecreaseException extends RuntimeException {

    public VehicleMileageCannotDecreaseException() {
        super("A quilometragem do veículo não pode diminuir");
    }
}
