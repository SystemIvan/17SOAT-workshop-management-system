package br.com.fiap.workshop_management_system.registration.vehicle.application.usecase;

import br.com.fiap.workshop_management_system.registration.customer.application.exception.CustomerNotFoundException;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.CustomerArchivedException;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import br.com.fiap.workshop_management_system.registration.vehicle.application.dto.CreateVehicleRequest;
import br.com.fiap.workshop_management_system.registration.vehicle.application.dto.VehicleMapper;
import br.com.fiap.workshop_management_system.registration.vehicle.application.dto.VehicleResponse;
import br.com.fiap.workshop_management_system.registration.vehicle.application.exception
        .VehicleChassisAlreadyExistsException;
import br.com.fiap.workshop_management_system.registration.vehicle.application.exception
        .VehicleLicensePlateAlreadyExistsException;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.ChassisNumber;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.LicensePlate;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Vehicle;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.VehicleYear;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Year;

@Service
public class CreateVehicleUseCase {

    private final CustomerRepository customerRepository;
    private final VehicleRepository vehicleRepository;
    private final Clock clock;

    public CreateVehicleUseCase(
            CustomerRepository customerRepository,
            VehicleRepository vehicleRepository,
            Clock clock) {
        this.customerRepository = customerRepository;
        this.vehicleRepository = vehicleRepository;
        this.clock = clock;
    }

    @Transactional
    public VehicleResponse execute(CreateVehicleRequest request) {
        LicensePlate licensePlate = new LicensePlate(request.licensePlate());
        ChassisNumber chassisNumber = request.chassis() == null ? null : new ChassisNumber(request.chassis());
        VehicleYear vehicleYear = VehicleYear.create(request.year(), Year.now(clock).getValue());
        Vehicle vehicle = Vehicle.create(request.customerId(), licensePlate, chassisNumber,
                request.brand(), request.model(), vehicleYear, request.color());

        Customer customer = customerRepository.findByIdForUpdate(request.customerId())
                .orElseThrow(CustomerNotFoundException::new);
        if (!customer.active()) {
            throw new CustomerArchivedException();
        }
        if (vehicleRepository.existsByLicensePlate(licensePlate)) {
            throw new VehicleLicensePlateAlreadyExistsException();
        }
        if (chassisNumber != null && vehicleRepository.existsByChassisNumber(chassisNumber)) {
            throw new VehicleChassisAlreadyExistsException();
        }

        vehicleRepository.save(vehicle);
        return VehicleMapper.toResponse(vehicle);
    }
}
