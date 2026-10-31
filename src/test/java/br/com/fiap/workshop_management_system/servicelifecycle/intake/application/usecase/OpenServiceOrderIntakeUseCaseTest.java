package br.com.fiap.workshop_management_system.servicelifecycle.intake.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.usecase.GenerateEstimateUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.Estimate;
import br.com.fiap.workshop_management_system.servicelifecycle.intake.application.dto.IntakeAddressRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.intake.application.dto.IntakeContactInfoRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.intake.application.dto.IntakeCustomerRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.intake.application.dto.IntakePartRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.intake.application.dto.IntakeServiceRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.intake.application.dto.IntakeVehicleRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.intake.application.dto.OpenServiceOrderIntakeRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.intake.application.dto.ServiceOrderIntakeResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto
        .AssignDiagnosisAssigneeRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.CreateServiceOrderRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.DiagnosisItemRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.MoneyDTO;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.PerformDiagnosisRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceExecutionResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderStatusLabel;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.StockRequirementRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .CatalogServiceArchivedForNewWorkException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .CatalogServiceNotFoundForNewWorkException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderIntakeCustomerArchivedException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderIntakeInvalidDataException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderIntakeVehicleChassisConflictException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderIntakeVehicleOwnershipConflictException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderStockItemInactiveException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderStockItemNotFoundException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderVehicleArchivedException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port
        .CatalogServiceEligibility;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.CatalogServiceQuotePort;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.CatalogServiceQuotePort
        .CatalogServiceQuote;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.CustomerIntakePort;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.StockItemQuotePort;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.StockItemQuotePort
        .StockItemQuote;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.VehicleIntakePort;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase
        .AssignDiagnosisAssigneeUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase
        .CreateServiceOrderUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase
        .GetServiceOrderUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase
        .PerformDiagnosisUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Priority;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrderStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenServiceOrderIntakeUseCaseTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID VEHICLE_ID = UUID.randomUUID();
    private static final UUID SERVICE_ORDER_ID = UUID.randomUUID();
    private static final UUID TECHNICIAN_ID = UUID.randomUUID();
    private static final UUID DIAGNOSIS_ID = UUID.randomUUID();
    private static final UUID ESTIMATE_ID = UUID.randomUUID();
    private static final UUID CATALOG_SERVICE_ID = UUID.randomUUID();
    private static final UUID STOCK_ITEM_ID = UUID.randomUUID();

    @Mock
    private CustomerIntakePort customerIntakePort;
    @Mock
    private VehicleIntakePort vehicleIntakePort;
    @Mock
    private CatalogServiceQuotePort catalogServiceQuotePort;
    @Mock
    private StockItemQuotePort stockItemQuotePort;
    @Mock
    private CreateServiceOrderUseCase createServiceOrderUseCase;
    @Mock
    private AssignDiagnosisAssigneeUseCase assignDiagnosisAssigneeUseCase;
    @Mock
    private PerformDiagnosisUseCase performDiagnosisUseCase;
    @Mock
    private GenerateEstimateUseCase generateEstimateUseCase;
    @Mock
    private GetServiceOrderUseCase getServiceOrderUseCase;

    private OpenServiceOrderIntakeUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new OpenServiceOrderIntakeUseCase(customerIntakePort, vehicleIntakePort, catalogServiceQuotePort,
                stockItemQuotePort, createServiceOrderUseCase, assignDiagnosisAssigneeUseCase,
                performDiagnosisUseCase, generateEstimateUseCase, getServiceOrderUseCase);
    }

    @Test
    void opensServiceOrderWithoutServicesUsingTheRegisteredVehicleSnapshot() {
        givenCustomer(CustomerIntakePort.Status.CREATED);
        givenVehicle(VehicleIntakePort.Status.REUSED);
        givenServiceOrderCreated();
        ServiceOrderResponse finalState = serviceOrder(List.of());
        when(getServiceOrderUseCase.execute(SERVICE_ORDER_ID)).thenReturn(finalState);

        ServiceOrderIntakeResponse response = useCase.execute(request(null, null));

        ArgumentCaptor<CreateServiceOrderRequest> captor = ArgumentCaptor.forClass(CreateServiceOrderRequest.class);
        verify(createServiceOrderUseCase).execute(captor.capture());
        CreateServiceOrderRequest created = captor.getValue();
        assertEquals(CUSTOMER_ID, created.customerId());
        assertEquals(VEHICLE_ID, created.vehicleId());
        assertEquals("ABC1D23", created.vehicleSnapshot().licensePlate());
        assertEquals("Fiat", created.vehicleSnapshot().brand());
        assertEquals("Argo", created.vehicleSnapshot().model());
        assertEquals(2022, created.vehicleSnapshot().year());
        assertEquals(Priority.HIGH, created.priority());
        assertEquals("Barulho na suspensão", created.initialAssessment());

        assertSame(finalState, response.serviceOrder());
        assertTrue(response.customer().created());
        assertFalse(response.vehicle().created());
        assertNull(response.estimateId());
        verifyNoInteractions(catalogServiceQuotePort, stockItemQuotePort, assignDiagnosisAssigneeUseCase,
                performDiagnosisUseCase, generateEstimateUseCase);
    }

    @Test
    void ignoresTechnicianWhenNoServicesAreInformed() {
        givenCustomer(CustomerIntakePort.Status.REUSED);
        givenVehicle(VehicleIntakePort.Status.REUSED);
        givenServiceOrderCreated();

        useCase.execute(request(TECHNICIAN_ID, List.of()));

        verifyNoInteractions(assignDiagnosisAssigneeUseCase, performDiagnosisUseCase, generateEstimateUseCase);
    }

    @Test
    void rejectsServicesWithoutTechnicianBeforeTouchingRegistrations() {
        assertThrows(ServiceOrderIntakeInvalidDataException.class,
                () -> useCase.execute(request(null, List.of(service()))));

        verifyNoInteractions(customerIntakePort, vehicleIntakePort, createServiceOrderUseCase);
    }

    @Test
    void diagnosesWithCatalogAndStockPricesAndGeneratesEstimateInOrder() {
        givenCustomer(CustomerIntakePort.Status.REUSED);
        givenVehicle(VehicleIntakePort.Status.CREATED);
        givenServiceOrderCreated();
        givenActiveQuotes();
        when(performDiagnosisUseCase.execute(eq(SERVICE_ORDER_ID), any()))
                .thenReturn(serviceOrder(List.of(execution())));
        givenEstimateGenerated();

        ServiceOrderIntakeResponse response = useCase.execute(request(TECHNICIAN_ID, List.of(service())));

        InOrder order = inOrder(customerIntakePort, vehicleIntakePort, createServiceOrderUseCase,
                assignDiagnosisAssigneeUseCase, performDiagnosisUseCase, generateEstimateUseCase);
        order.verify(customerIntakePort).identifyOrRegister(any());
        order.verify(vehicleIntakePort).identifyOrRegister(any());
        order.verify(createServiceOrderUseCase).execute(any());
        order.verify(assignDiagnosisAssigneeUseCase)
                .execute(SERVICE_ORDER_ID, new AssignDiagnosisAssigneeRequest(TECHNICIAN_ID));
        order.verify(performDiagnosisUseCase).execute(eq(SERVICE_ORDER_ID), any());
        order.verify(generateEstimateUseCase).execute(SERVICE_ORDER_ID, DIAGNOSIS_ID);

        ArgumentCaptor<PerformDiagnosisRequest> captor = ArgumentCaptor.forClass(PerformDiagnosisRequest.class);
        verify(performDiagnosisUseCase).execute(eq(SERVICE_ORDER_ID), captor.capture());
        PerformDiagnosisRequest diagnosis = captor.getValue();
        assertEquals(TECHNICIAN_ID, diagnosis.diagnosedByTechnicianId());
        DiagnosisItemRequest item = diagnosis.items().getFirst();
        assertEquals(CATALOG_SERVICE_ID, item.catalogServiceId());
        assertEquals("Troca de óleo", item.name());
        assertEquals(new MoneyDTO(new BigDecimal("150.00"), "BRL"), item.price());
        StockRequirementRequest part = item.stockRequirements().getFirst();
        assertEquals(STOCK_ITEM_ID, part.stockItemId());
        assertEquals(StockItemType.PART, part.type());
        assertEquals(2, part.quantity());
        assertEquals("Filtro de óleo", part.nameSnapshot());
        assertEquals(new MoneyDTO(new BigDecimal("45.90"), "BRL"), part.priceSnapshot());

        assertEquals(ESTIMATE_ID, response.estimateId());
        assertTrue(response.vehicle().created());
    }

    @Test
    void passesMissingContactDataAsNullsToTheRegistration() {
        givenCustomer(CustomerIntakePort.Status.REUSED);
        givenVehicle(VehicleIntakePort.Status.REUSED);
        givenServiceOrderCreated();

        useCase.execute(new OpenServiceOrderIntakeRequest(
                new IntakeCustomerRequest("529.982.247-25", null, null), vehicleRequest(), null,
                "Triagem", null, null));

        ArgumentCaptor<CustomerIntakePort.CustomerData> captor =
                ArgumentCaptor.forClass(CustomerIntakePort.CustomerData.class);
        verify(customerIntakePort).identifyOrRegister(captor.capture());
        assertEquals("529.982.247-25", captor.getValue().document());
        assertNull(captor.getValue().email());
        assertNull(captor.getValue().address());
    }

    @Test
    void passesEveryAddressFieldToTheRegistration() {
        givenCustomer(CustomerIntakePort.Status.CREATED);
        givenVehicle(VehicleIntakePort.Status.CREATED);
        givenServiceOrderCreated();
        IntakeAddressRequest address = new IntakeAddressRequest("Avenida Paulista", "1000", "Conjunto 101",
                "Bela Vista", "São Paulo", "SP", "01310-100");

        useCase.execute(new OpenServiceOrderIntakeRequest(
                new IntakeCustomerRequest("529.982.247-25", "Cliente",
                        new IntakeContactInfoRequest("cliente@example.test", "+5511999999999", address)),
                vehicleRequest(), null, "Triagem", null, null));

        ArgumentCaptor<CustomerIntakePort.CustomerData> captor =
                ArgumentCaptor.forClass(CustomerIntakePort.CustomerData.class);
        verify(customerIntakePort).identifyOrRegister(captor.capture());
        assertEquals(new CustomerIntakePort.AddressData("Avenida Paulista", "1000", "Conjunto 101", "Bela Vista",
                "São Paulo", "SP", "01310-100"), captor.getValue().address());
    }

    @Test
    void rejectsArchivedCustomerBeforeIdentifyingTheVehicle() {
        givenCustomer(CustomerIntakePort.Status.ARCHIVED);

        assertThrows(ServiceOrderIntakeCustomerArchivedException.class, () -> useCase.execute(request(null, null)));

        verifyNoInteractions(vehicleIntakePort, createServiceOrderUseCase);
    }

    @ParameterizedTest
    @EnumSource(value = VehicleIntakePort.Status.class,
            names = {"ARCHIVED", "OWNED_BY_ANOTHER_CUSTOMER", "CHASSIS_ALREADY_REGISTERED"})
    void rejectsVehicleThatCannotReceiveNewWork(VehicleIntakePort.Status status) {
        givenCustomer(CustomerIntakePort.Status.REUSED);
        when(vehicleIntakePort.identifyOrRegister(any()))
                .thenReturn(new VehicleIntakePort.VehicleIntake(null, status, null, null, null, null));

        Class<? extends RuntimeException> expected = switch (status) {
            case ARCHIVED -> ServiceOrderVehicleArchivedException.class;
            case OWNED_BY_ANOTHER_CUSTOMER -> ServiceOrderIntakeVehicleOwnershipConflictException.class;
            default -> ServiceOrderIntakeVehicleChassisConflictException.class;
        };
        assertThrows(expected, () -> useCase.execute(request(null, null)));

        verifyNoInteractions(createServiceOrderUseCase);
    }

    @ParameterizedTest
    @EnumSource(value = CatalogServiceEligibility.class, names = {"ARCHIVED", "NOT_FOUND"})
    void rejectsUnavailableCatalogServiceBeforeAnyDiagnosisWork(CatalogServiceEligibility eligibility) {
        givenCustomer(CustomerIntakePort.Status.REUSED);
        givenVehicle(VehicleIntakePort.Status.REUSED);
        givenServiceOrderCreated();
        when(catalogServiceQuotePort.quote(CATALOG_SERVICE_ID))
                .thenReturn(new CatalogServiceQuote(CATALOG_SERVICE_ID, eligibility, null, null));

        Class<? extends RuntimeException> expected = eligibility == CatalogServiceEligibility.ARCHIVED
                ? CatalogServiceArchivedForNewWorkException.class
                : CatalogServiceNotFoundForNewWorkException.class;
        assertThrows(expected, () -> useCase.execute(request(TECHNICIAN_ID, List.of(service()))));

        verifyNoInteractions(assignDiagnosisAssigneeUseCase, performDiagnosisUseCase, generateEstimateUseCase);
    }

    @Test
    void rejectsUnknownStockItem() {
        givenCustomer(CustomerIntakePort.Status.REUSED);
        givenVehicle(VehicleIntakePort.Status.REUSED);
        givenServiceOrderCreated();
        givenActiveCatalogQuote();
        when(stockItemQuotePort.quote(List.of(STOCK_ITEM_ID))).thenReturn(Map.of());

        assertThrows(ServiceOrderStockItemNotFoundException.class,
                () -> useCase.execute(request(TECHNICIAN_ID, List.of(service()))));

        verifyNoInteractions(assignDiagnosisAssigneeUseCase, performDiagnosisUseCase);
    }

    @Test
    void rejectsInactiveStockItem() {
        givenCustomer(CustomerIntakePort.Status.REUSED);
        givenVehicle(VehicleIntakePort.Status.REUSED);
        givenServiceOrderCreated();
        givenActiveCatalogQuote();
        when(stockItemQuotePort.quote(List.of(STOCK_ITEM_ID))).thenReturn(Map.of(STOCK_ITEM_ID,
                new StockItemQuote(STOCK_ITEM_ID, false, "Filtro", StockItemType.PART, Money.brl(BigDecimal.ONE))));

        assertThrows(ServiceOrderStockItemInactiveException.class,
                () -> useCase.execute(request(TECHNICIAN_ID, List.of(service()))));

        verifyNoInteractions(assignDiagnosisAssigneeUseCase, performDiagnosisUseCase);
    }

    @Test
    void diagnosesServiceWithoutPartsWithoutQuotingStock() {
        givenCustomer(CustomerIntakePort.Status.REUSED);
        givenVehicle(VehicleIntakePort.Status.REUSED);
        givenServiceOrderCreated();
        givenActiveCatalogQuote();
        when(performDiagnosisUseCase.execute(eq(SERVICE_ORDER_ID), any()))
                .thenReturn(serviceOrder(List.of(execution())));
        givenEstimateGenerated();

        useCase.execute(request(TECHNICIAN_ID, List.of(new IntakeServiceRequest(CATALOG_SERVICE_ID, null))));

        verifyNoInteractions(stockItemQuotePort);
    }

    private void givenCustomer(CustomerIntakePort.Status status) {
        UUID id = status == CustomerIntakePort.Status.ARCHIVED ? null : CUSTOMER_ID;
        when(customerIntakePort.identifyOrRegister(any()))
                .thenReturn(new CustomerIntakePort.CustomerIntake(id, status));
    }

    private void givenVehicle(VehicleIntakePort.Status status) {
        when(vehicleIntakePort.identifyOrRegister(any())).thenReturn(
                new VehicleIntakePort.VehicleIntake(VEHICLE_ID, status, "ABC1D23", "Fiat", "Argo", 2022));
    }

    private void givenServiceOrderCreated() {
        when(createServiceOrderUseCase.execute(any())).thenReturn(serviceOrder(List.of()));
    }

    private void givenActiveCatalogQuote() {
        when(catalogServiceQuotePort.quote(CATALOG_SERVICE_ID)).thenReturn(new CatalogServiceQuote(
                CATALOG_SERVICE_ID, CatalogServiceEligibility.ACTIVE, "Troca de óleo",
                Money.brl(new BigDecimal("150.00"))));
    }

    private void givenActiveQuotes() {
        givenActiveCatalogQuote();
        when(stockItemQuotePort.quote(List.of(STOCK_ITEM_ID))).thenReturn(Map.of(STOCK_ITEM_ID, new StockItemQuote(
                STOCK_ITEM_ID, true, "Filtro de óleo", StockItemType.PART, Money.brl(new BigDecimal("45.90")))));
    }

    private void givenEstimateGenerated() {
        Estimate estimate = mock(Estimate.class);
        when(estimate.id()).thenReturn(ESTIMATE_ID);
        when(generateEstimateUseCase.execute(SERVICE_ORDER_ID, DIAGNOSIS_ID))
                .thenReturn(new GenerateEstimateUseCase.Result(estimate, null));
    }

    private static OpenServiceOrderIntakeRequest request(UUID technicianId, List<IntakeServiceRequest> services) {
        return new OpenServiceOrderIntakeRequest(
                new IntakeCustomerRequest("529.982.247-25", "Cliente",
                        new IntakeContactInfoRequest("cliente@example.test", "+5511999999999", null)),
                vehicleRequest(), Priority.HIGH, "Barulho na suspensão", technicianId, services);
    }

    private static IntakeVehicleRequest vehicleRequest() {
        return new IntakeVehicleRequest("ABC1D23", "Fiat", "Argo", 2022, "Prata", null, null);
    }

    private static IntakeServiceRequest service() {
        return new IntakeServiceRequest(CATALOG_SERVICE_ID, List.of(new IntakePartRequest(STOCK_ITEM_ID, 2)));
    }

    private static ServiceExecutionResponse execution() {
        return new ServiceExecutionResponse(UUID.randomUUID(), DIAGNOSIS_ID, CATALOG_SERVICE_ID, "Troca de óleo",
                new MoneyDTO(new BigDecimal("150.00"), "BRL"), null, null, null, TECHNICIAN_ID, null, null,
                List.of(), List.of());
    }

    private static ServiceOrderResponse serviceOrder(List<ServiceExecutionResponse> executions) {
        return new ServiceOrderResponse(SERVICE_ORDER_ID, CUSTOMER_ID, VEHICLE_ID, null, Priority.NORMAL, "Triagem",
                null, ServiceOrderStatus.RECEIVED, ServiceOrderStatus.RECEIVED, ServiceOrderStatusLabel.RECEBIDA,
                Set.of(), executions);
    }
}
