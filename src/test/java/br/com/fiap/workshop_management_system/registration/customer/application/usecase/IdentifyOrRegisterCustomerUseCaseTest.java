package br.com.fiap.workshop_management_system.registration.customer.application.usecase;

import br.com.fiap.workshop_management_system.registration.customer.application.api.CustomerIntakeAddress;
import br.com.fiap.workshop_management_system.registration.customer.application.api.CustomerIntakeCommand;
import br.com.fiap.workshop_management_system.registration.customer.application.api.CustomerIntakeConflictException;
import br.com.fiap.workshop_management_system.registration.customer.application.api.CustomerIntakeOutcome;
import br.com.fiap.workshop_management_system.registration.customer.application.api.CustomerIntakeResult;
import br.com.fiap.workshop_management_system.registration.customer.application.exception
        .CustomerTaxIdAlreadyExistsException;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.ContactInfo;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentifyOrRegisterCustomerUseCaseTest {

    private static final String DOCUMENT = "529.982.247-25";
    private static final TaxId TAX_ID = new TaxId(DOCUMENT);

    @Mock
    private CustomerRepository repository;

    private IdentifyOrRegisterCustomerUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new IdentifyOrRegisterCustomerUseCase(repository);
    }

    @Test
    void reusesActiveCustomerWithoutChangingItsData() {
        Customer existing = Customer.create("Nome Original", TAX_ID,
                new ContactInfo("original@example.test", "+5511999999999"));
        when(repository.findActiveByTaxId(TAX_ID)).thenReturn(Optional.of(existing));

        CustomerIntakeResult result = useCase.identifyOrRegister(
                new CustomerIntakeCommand(DOCUMENT, "Outro Nome", "outro@example.test", "+5511888888888", null));

        assertEquals(CustomerIntakeOutcome.REUSED, result.outcome());
        assertEquals(existing.id(), result.customerId());
        assertEquals("Nome Original", existing.name());
        assertEquals("original@example.test", existing.contactInfo().email().value());
        verify(repository, never()).save(any());
    }

    @Test
    void reportsArchivedCustomerWithoutRegisteringAnother() {
        when(repository.findActiveByTaxId(TAX_ID)).thenReturn(Optional.empty());
        when(repository.existsByTaxId(TAX_ID)).thenReturn(true);

        CustomerIntakeResult result = useCase.identifyOrRegister(command());

        assertEquals(CustomerIntakeOutcome.ARCHIVED, result.outcome());
        assertNull(result.customerId());
        verify(repository, never()).save(any());
    }

    @Test
    void registersNewCustomerWithNormalizedDocumentAndAddress() {
        when(repository.findActiveByTaxId(TAX_ID)).thenReturn(Optional.empty());
        when(repository.existsByTaxId(TAX_ID)).thenReturn(false);
        CustomerIntakeAddress address = new CustomerIntakeAddress(
                "Rua Exemplo", "100", null, "Centro", "São Paulo", "SP", "01001-000");

        CustomerIntakeResult result = useCase.identifyOrRegister(
                new CustomerIntakeCommand(DOCUMENT, "Cliente Novo", "novo@example.test", "+5511999999999", address));

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(repository).save(captor.capture());
        Customer saved = captor.getValue();
        assertEquals(CustomerIntakeOutcome.CREATED, result.outcome());
        assertEquals(saved.id(), result.customerId());
        assertEquals(TAX_ID, saved.taxId());
        assertEquals("Cliente Novo", saved.name());
        assertEquals("Rua Exemplo", saved.contactInfo().address().street());
    }

    @Test
    void rejectsNewCustomerWithoutContactData() {
        when(repository.findActiveByTaxId(TAX_ID)).thenReturn(Optional.empty());
        when(repository.existsByTaxId(TAX_ID)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> useCase.identifyOrRegister(
                new CustomerIntakeCommand(DOCUMENT, "Cliente Novo", null, null, null)));
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsNewCustomerWithoutName() {
        when(repository.findActiveByTaxId(TAX_ID)).thenReturn(Optional.empty());
        when(repository.existsByTaxId(TAX_ID)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> useCase.identifyOrRegister(
                new CustomerIntakeCommand(DOCUMENT, " ", "novo@example.test", "+5511999999999", null)));
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsInvalidDocumentBeforeAnyLookup() {
        assertThrows(IllegalArgumentException.class, () -> useCase.identifyOrRegister(
                new CustomerIntakeCommand("123", "Cliente", "novo@example.test", "+5511999999999", null)));
        verify(repository, never()).findActiveByTaxId(any());
    }

    @Test
    void translatesConcurrentRegistrationIntoIntakeConflict() {
        when(repository.findActiveByTaxId(TAX_ID)).thenReturn(Optional.empty());
        when(repository.existsByTaxId(TAX_ID)).thenReturn(false);
        doThrow(new CustomerTaxIdAlreadyExistsException()).when(repository).save(any());

        assertThrows(CustomerIntakeConflictException.class, () -> useCase.identifyOrRegister(command()));
    }

    private static CustomerIntakeCommand command() {
        return new CustomerIntakeCommand(DOCUMENT, "Cliente Novo", "novo@example.test", "+5511999999999", null);
    }
}
