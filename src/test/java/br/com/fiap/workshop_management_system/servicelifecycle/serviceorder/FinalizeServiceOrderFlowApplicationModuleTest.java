package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder;

import br.com.fiap.workshop_management_system.registration.customer.domain.model.ContactInfo;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.FinalizeServiceOrderRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase.FinalizeServiceOrderUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.DiagnosisItem;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrderStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.VehicleSnapshot;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Boots the real servicelifecycle + registration Spring context (no mocks) to prove the
 * new servicelifecycle -> registration.customer dependency wires correctly end-to-end:
 * real JPA persistence, real CustomerRepository bean, real notification adapter.
 */
@ApplicationModuleTest(ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
class FinalizeServiceOrderFlowApplicationModuleTest {

    @Autowired
    private ServiceOrderRepository serviceOrderRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private FinalizeServiceOrderUseCase finalizeServiceOrderUseCase;

    @Test
    @Transactional
    void finalizingAServiceOrderResolvesTheRealCustomerAcrossModuleBoundaries() {
        Customer customer = Customer.create("Jane Doe", new TaxId(randomValidCpf()),
                new ContactInfo("jane.doe@example.com", "11999999999"));
        customerRepository.save(customer);

        ServiceOrder serviceOrder = ServiceOrder.create(
                customer.id(), UUID.randomUUID(), new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015));
        DiagnosisItem item = new DiagnosisItem(UUID.randomUUID(), "Troca de óleo", Money.brl(BigDecimal.TEN), List.of());
        serviceOrder.performDiagnosis(List.of(item));
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);
        serviceOrder.startExecution(executionId);
        serviceOrder.completeExecution(executionId);
        serviceOrderRepository.save(serviceOrder);

        ServiceOrderResponse response = finalizeServiceOrderUseCase.execute(
                serviceOrder.id(), new FinalizeServiceOrderRequest(true));

        assertEquals(ServiceOrderStatus.DELIVERED, response.status());
    }

    /**
     * Generates a fresh, checksum-valid CPF per invocation so this test never collides with a
     * TaxId already committed (uncommitted-rollback) by another test sharing the same H2 instance
     * within the same JVM fork - e.g. CustomerRepositoryIntegrationTest, which is a plain
     * @SpringBootTest with no @Transactional rollback.
     */
    private static String randomValidCpf() {
        String base = "%09d".formatted(Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1_000_000_000L));
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
