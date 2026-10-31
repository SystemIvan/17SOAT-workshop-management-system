package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.registration;

import br.com.fiap.workshop_management_system.registration.vehicle.application.api.VehicleIntakeApi;
import br.com.fiap.workshop_management_system.registration.vehicle.application.api.VehicleIntakeCommand;
import br.com.fiap.workshop_management_system.registration.vehicle.application.api.VehicleIntakeConflictException;
import br.com.fiap.workshop_management_system.registration.vehicle.application.api.VehicleIntakeResult;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderIntakeConcurrentRegistrationException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderIntakeInvalidDataException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.VehicleIntakePort;
import org.springframework.stereotype.Component;

@Component
public class RegistrationVehicleIntakeAdapter implements VehicleIntakePort {

    private final VehicleIntakeApi vehicleIntakeApi;

    public RegistrationVehicleIntakeAdapter(VehicleIntakeApi vehicleIntakeApi) {
        this.vehicleIntakeApi = vehicleIntakeApi;
    }

    @Override
    public VehicleIntake identifyOrRegister(VehicleData data) {
        VehicleIntakeResult result;
        try {
            result = vehicleIntakeApi.identifyOrRegister(new VehicleIntakeCommand(data.customerId(),
                    data.licensePlate(), data.chassis(), data.brand(), data.model(), data.year(), data.color(),
                    data.mileage()));
        } catch (IllegalArgumentException exception) {
            // Registration validation messages never echo the submitted plate or chassis.
            throw new ServiceOrderIntakeInvalidDataException(exception.getMessage());
        } catch (VehicleIntakeConflictException exception) {
            throw new ServiceOrderIntakeConcurrentRegistrationException();
        }
        Status status = switch (result.outcome()) {
            case REUSED -> Status.REUSED;
            case CREATED -> Status.CREATED;
            case ARCHIVED -> Status.ARCHIVED;
            case OWNED_BY_ANOTHER_CUSTOMER -> Status.OWNED_BY_ANOTHER_CUSTOMER;
            case CHASSIS_ALREADY_REGISTERED -> Status.CHASSIS_ALREADY_REGISTERED;
        };
        return new VehicleIntake(result.vehicleId(), status, result.licensePlate(), result.brand(), result.model(),
                result.year());
    }
}
