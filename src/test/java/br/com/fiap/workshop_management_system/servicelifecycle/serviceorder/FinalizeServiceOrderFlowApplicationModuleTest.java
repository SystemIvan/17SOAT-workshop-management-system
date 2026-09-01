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
        Customer customer = Customer.create("Jane Doe", new TaxId("98765432100"),
                new ContactInfo("jane.doe@example.com", "11999999999"));
        customerRepository.save(customer);

        ServiceOrder serviceOrder = ServiceOrder.create(
                customer.id(), UUID.randomUUID(), new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015),
                "Initial assessment");
        serviceOrder.assignDiagnosisAssignee(UUID.randomUUID());
        DiagnosisItem item = new DiagnosisItem(UUID.randomUUID(), "Troca de óleo", Money.brl(BigDecimal.TEN), List.of());
        serviceOrder.performDiagnosis(List.of(item), UUID.randomUUID(), java.time.Instant.EPOCH);
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);
        serviceOrder.confirmTechnicianAssignment(executionId, UUID.randomUUID());
        serviceOrder.startExecution(executionId, java.time.Instant.now());
        serviceOrder.completeExecution(executionId, java.time.Instant.now());
        serviceOrderRepository.save(serviceOrder);

        ServiceOrderResponse response = finalizeServiceOrderUseCase.execute(
                serviceOrder.id(), new FinalizeServiceOrderRequest(true));

        assertEquals(ServiceOrderStatus.DELIVERED, response.status());
    }
}
