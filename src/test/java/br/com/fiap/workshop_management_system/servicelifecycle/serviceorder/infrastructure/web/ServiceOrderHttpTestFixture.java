package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.web;

import br.com.fiap.workshop_management_system.registration.customer.domain.model.ContactInfo;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.LicensePlate;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Vehicle;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.VehicleYear;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.repository.VehicleRepository;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class ServiceOrderHttpTestFixture {

    private static final AtomicInteger CPF_SEQUENCE = new AtomicInteger(950_000_000);
    private static final AtomicInteger PLATE_SEQUENCE = new AtomicInteger(6000);

    private ServiceOrderHttpTestFixture() {
    }

    public static void persistActiveVehicle(WebApplicationContext context, UUID customerId, UUID vehicleId) {
        Customer customer = Customer.reconstitute(
                customerId,
                "Cliente Service Order",
                new TaxId(nextValidCpf()),
                new ContactInfo("service-order-fixture@example.test", "+5511999999999"),
                true);
        context.getBean(CustomerRepository.class).save(customer);
        Vehicle vehicle = Vehicle.reconstitute(
                vehicleId,
                customerId,
                new LicensePlate(nextPlate()),
                null,
                "Fiat",
                "Uno",
                VehicleYear.create(2015, 2026),
                "Prata",
                null,
                true);
        context.getBean(VehicleRepository.class).save(vehicle);
    }

    private static String nextPlate() {
        return "SOT%04d".formatted(PLATE_SEQUENCE.getAndIncrement());
    }

    private static String nextValidCpf() {
        String base = "%09d".formatted(CPF_SEQUENCE.getAndIncrement());
        int firstCheckDigit = calculateCpfCheckDigit(base);
        String partialCpf = base + firstCheckDigit;
        return partialCpf + calculateCpfCheckDigit(partialCpf);
    }

    private static int calculateCpfCheckDigit(String digits) {
        int sum = 0;
        for (int index = 0; index < digits.length(); index++) {
            sum += (digits.charAt(index) - '0') * (digits.length() + 1 - index);
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }
}
