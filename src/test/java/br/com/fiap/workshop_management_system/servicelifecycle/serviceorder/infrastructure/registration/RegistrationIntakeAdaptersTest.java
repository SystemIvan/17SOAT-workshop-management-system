package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.registration;

import br.com.fiap.workshop_management_system.registration.customer.application.api.CustomerIntakeApi;
import br.com.fiap.workshop_management_system.registration.customer.application.api.CustomerIntakeCommand;
import br.com.fiap.workshop_management_system.registration.customer.application.api.CustomerIntakeConflictException;
import br.com.fiap.workshop_management_system.registration.customer.application.api.CustomerIntakeOutcome;
import br.com.fiap.workshop_management_system.registration.customer.application.api.CustomerIntakeResult;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.api
        .CatalogServiceAvailability;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.api.CatalogServiceQuote;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.api.CatalogServiceQuoteApi;
import br.com.fiap.workshop_management_system.registration.vehicle.application.api.VehicleIntakeApi;
import br.com.fiap.workshop_management_system.registration.vehicle.application.api.VehicleIntakeConflictException;
import br.com.fiap.workshop_management_system.registration.vehicle.application.api.VehicleIntakeOutcome;
import br.com.fiap.workshop_management_system.registration.vehicle.application.api.VehicleIntakeResult;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderIntakeConcurrentRegistrationException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderIntakeInvalidDataException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port
        .CatalogServiceEligibility;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.CatalogServiceQuotePort;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.CustomerIntakePort;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.VehicleIntakePort;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrationIntakeAdaptersTest {

    private static final CustomerIntakePort.CustomerData CUSTOMER = new CustomerIntakePort.CustomerData(
            "529.982.247-25", "Cliente", "cliente@example.test", "+5511999999999",
            new CustomerIntakePort.AddressData("Rua A", "1", null, "Centro", "São Paulo", "SP", "01001-000"));

    private static final VehicleIntakePort.VehicleData VEHICLE = new VehicleIntakePort.VehicleData(
            UUID.randomUUID(), "ABC1D23", null, "Fiat", "Argo", 2022, "Prata", null);

    @ParameterizedTest
    @EnumSource(CustomerIntakeOutcome.class)
    void mapsEveryCustomerOutcome(CustomerIntakeOutcome outcome) {
        CustomerIntakeApi api = mock(CustomerIntakeApi.class);
        UUID customerId = UUID.randomUUID();
        when(api.identifyOrRegister(any())).thenReturn(new CustomerIntakeResult(customerId, outcome));

        CustomerIntakePort.CustomerIntake result = new RegistrationCustomerIntakeAdapter(api)
                .identifyOrRegister(CUSTOMER);

        assertEquals(CustomerIntakePort.Status.valueOf(outcome.name()), result.status());
        assertEquals(customerId, result.customerId());
    }

    @Test
    void passesCustomerDataIncludingAddress() {
        CustomerIntakeApi api = mock(CustomerIntakeApi.class);
        when(api.identifyOrRegister(any()))
                .thenReturn(new CustomerIntakeResult(UUID.randomUUID(), CustomerIntakeOutcome.CREATED));

        new RegistrationCustomerIntakeAdapter(api).identifyOrRegister(CUSTOMER);

        ArgumentCaptor<CustomerIntakeCommand> captor = ArgumentCaptor.forClass(CustomerIntakeCommand.class);
        verify(api).identifyOrRegister(captor.capture());
        assertEquals("529.982.247-25", captor.getValue().document());
        assertEquals("Rua A", captor.getValue().address().street());
        assertEquals("01001-000", captor.getValue().address().postalCode());
    }

    @Test
    void translatesCustomerValidationAndConflictFailures() {
        CustomerIntakeApi api = mock(CustomerIntakeApi.class);
        RegistrationCustomerIntakeAdapter adapter = new RegistrationCustomerIntakeAdapter(api);

        when(api.identifyOrRegister(any())).thenThrow(new IllegalArgumentException("CPF/CNPJ inválido"));
        ServiceOrderIntakeInvalidDataException invalid = assertThrows(
                ServiceOrderIntakeInvalidDataException.class, () -> adapter.identifyOrRegister(CUSTOMER));
        assertEquals("CPF/CNPJ inválido", invalid.getMessage());

        doThrow(new CustomerIntakeConflictException()).when(api).identifyOrRegister(any());
        assertThrows(ServiceOrderIntakeConcurrentRegistrationException.class,
                () -> adapter.identifyOrRegister(CUSTOMER));
    }

    @ParameterizedTest
    @EnumSource(VehicleIntakeOutcome.class)
    void mapsEveryVehicleOutcome(VehicleIntakeOutcome outcome) {
        VehicleIntakeApi api = mock(VehicleIntakeApi.class);
        UUID vehicleId = UUID.randomUUID();
        when(api.identifyOrRegister(any()))
                .thenReturn(new VehicleIntakeResult(vehicleId, outcome, "ABC1D23", "Fiat", "Argo", 2022));

        VehicleIntakePort.VehicleIntake result = new RegistrationVehicleIntakeAdapter(api).identifyOrRegister(VEHICLE);

        assertEquals(VehicleIntakePort.Status.valueOf(outcome.name()), result.status());
        assertEquals(vehicleId, result.vehicleId());
        assertEquals("ABC1D23", result.licensePlate());
        assertEquals("Fiat", result.brand());
        assertEquals("Argo", result.model());
        assertEquals(2022, result.year());
    }

    @Test
    void translatesVehicleValidationAndConflictFailures() {
        VehicleIntakeApi api = mock(VehicleIntakeApi.class);
        RegistrationVehicleIntakeAdapter adapter = new RegistrationVehicleIntakeAdapter(api);

        when(api.identifyOrRegister(any()))
                .thenThrow(new IllegalArgumentException("A placa do veículo possui formato inválido"));
        assertThrows(ServiceOrderIntakeInvalidDataException.class, () -> adapter.identifyOrRegister(VEHICLE));

        doThrow(new VehicleIntakeConflictException()).when(api).identifyOrRegister(any());
        assertThrows(ServiceOrderIntakeConcurrentRegistrationException.class,
                () -> adapter.identifyOrRegister(VEHICLE));
    }

    @ParameterizedTest
    @EnumSource(CatalogServiceAvailability.class)
    void mapsEveryCatalogServiceAvailability(CatalogServiceAvailability availability) {
        CatalogServiceQuoteApi api = mock(CatalogServiceQuoteApi.class);
        UUID id = UUID.randomUUID();
        when(api.quote(id)).thenReturn(new CatalogServiceQuote(id, availability, "Troca de óleo",
                new BigDecimal("150.00"), "BRL"));

        CatalogServiceQuotePort.CatalogServiceQuote quote = new RegistrationCatalogServiceQuoteAdapter(api).quote(id);

        assertEquals(CatalogServiceEligibility.valueOf(availability.name()), quote.eligibility());
        assertEquals("Troca de óleo", quote.name());
        assertEquals(new Money(new BigDecimal("150.00"), "BRL"), quote.price());
    }

    @Test
    void keepsMissingCatalogServicePriceEmpty() {
        CatalogServiceQuoteApi api = mock(CatalogServiceQuoteApi.class);
        UUID id = UUID.randomUUID();
        when(api.quote(id)).thenReturn(CatalogServiceQuote.notFound(id));

        CatalogServiceQuotePort.CatalogServiceQuote quote = new RegistrationCatalogServiceQuoteAdapter(api).quote(id);

        assertEquals(CatalogServiceEligibility.NOT_FOUND, quote.eligibility());
        assertNull(quote.price());
    }
}
