package br.com.fiap.workshop_management_system.registration.vehicle.application.usecase;

import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import br.com.fiap.workshop_management_system.registration.vehicle.application.api.VehicleIntakeApi;
import br.com.fiap.workshop_management_system.registration.vehicle.application.api.VehicleIntakeCommand;
import br.com.fiap.workshop_management_system.registration.vehicle.application.api.VehicleIntakeConflictException;
import br.com.fiap.workshop_management_system.registration.vehicle.application.api.VehicleIntakeOutcome;
import br.com.fiap.workshop_management_system.registration.vehicle.application.api.VehicleIntakeResult;
import br.com.fiap.workshop_management_system.registration.vehicle.application.exception
        .VehicleChassisAlreadyExistsException;
import br.com.fiap.workshop_management_system.registration.vehicle.application.exception
        .VehicleLicensePlateAlreadyExistsException;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.ChassisNumber;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.LicensePlate;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Mileage;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Vehicle;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.VehicleYear;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Year;
import java.util.Objects;

@Service
public class IdentifyOrRegisterVehicleUseCase implements VehicleIntakeApi {

    private final CustomerRepository customerRepository;
    private final VehicleRepository vehicleRepository;
    private final Clock clock;

    public IdentifyOrRegisterVehicleUseCase(
            CustomerRepository customerRepository,
            VehicleRepository vehicleRepository,
            Clock clock) {
        this.customerRepository = customerRepository;
        this.vehicleRepository = vehicleRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public VehicleIntakeResult identifyOrRegister(VehicleIntakeCommand command) {
        Objects.requireNonNull(command, "Os dados do veículo são obrigatórios");
        Objects.requireNonNull(command.customerId(), "O cliente do veículo é obrigatório");
        LicensePlate licensePlate = new LicensePlate(command.licensePlate());
        return vehicleRepository.findByLicensePlateForUpdate(licensePlate)
                .map(vehicle -> existing(vehicle, command))
                .orElseGet(() -> register(licensePlate, command));
    }

    private static VehicleIntakeResult existing(Vehicle vehicle, VehicleIntakeCommand command) {
        if (!vehicle.active()) {
            return VehicleIntakeResult.rejected(VehicleIntakeOutcome.ARCHIVED);
        }
        if (!vehicle.customerId().equals(command.customerId())) {
            return VehicleIntakeResult.rejected(VehicleIntakeOutcome.OWNED_BY_ANOTHER_CUSTOMER);
        }
        return toResult(vehicle, VehicleIntakeOutcome.REUSED);
    }

    private VehicleIntakeResult register(LicensePlate licensePlate, VehicleIntakeCommand command) {
        if (command.year() == null) {
            throw new IllegalArgumentException("O ano do veículo é obrigatório");
        }
        ChassisNumber chassisNumber = command.chassis() == null ? null : new ChassisNumber(command.chassis());
        VehicleYear year = VehicleYear.create(command.year(), Year.now(clock).getValue());
        Mileage mileage = command.mileage() == null ? null : new Mileage(command.mileage());
        Vehicle vehicle = Vehicle.create(command.customerId(), licensePlate, chassisNumber,
                command.brand(), command.model(), year, command.color(), mileage);

        // Same Customer lock as CreateVehicleUseCase, so a concurrent archive cannot interleave with the insert.
        boolean customerActive = customerRepository.findByIdForUpdate(command.customerId())
                .map(Customer::active)
                .orElse(false);
        if (!customerActive) {
            throw new IllegalStateException("O cliente do veículo deve existir e estar ativo");
        }
        if (chassisNumber != null && vehicleRepository.existsByChassisNumber(chassisNumber)) {
            return VehicleIntakeResult.rejected(VehicleIntakeOutcome.CHASSIS_ALREADY_REGISTERED);
        }
        try {
            vehicleRepository.save(vehicle);
        } catch (VehicleLicensePlateAlreadyExistsException | VehicleChassisAlreadyExistsException exception) {
            throw new VehicleIntakeConflictException();
        }
        return toResult(vehicle, VehicleIntakeOutcome.CREATED);
    }

    private static VehicleIntakeResult toResult(Vehicle vehicle, VehicleIntakeOutcome outcome) {
        return new VehicleIntakeResult(vehicle.id(), outcome, vehicle.licensePlate().value(), vehicle.brand(),
                vehicle.model(), vehicle.year().value());
    }
}
