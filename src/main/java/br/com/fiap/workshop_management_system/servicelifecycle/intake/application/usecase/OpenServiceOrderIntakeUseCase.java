package br.com.fiap.workshop_management_system.servicelifecycle.intake.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.usecase.GenerateEstimateUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.intake.application.dto.IntakeAddressRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.intake.application.dto.IntakeContactInfoRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.intake.application.dto.IntakeCustomerRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.intake.application.dto.IntakePartRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.intake.application.dto.IntakeServiceRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.intake.application.dto.IntakeVehicleRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.intake.application.dto.OpenServiceOrderIntakeRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.intake.application.dto.ServiceOrderIntakeResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.intake.application.dto.ServiceOrderIntakeResponse
        .RegistrationReference;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto
        .AssignDiagnosisAssigneeRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.CreateServiceOrderRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.DiagnosisItemRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.MoneyDTO;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.PerformDiagnosisRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.StockRequirementRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.VehicleSnapshotRequest;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Opens a Service Order from business data in a single transaction (technical-spec D1): the existing use cases are
 * reused, so creation, diagnosis, stock assessment and Estimate rules stay in one place. Every participant joins this
 * transaction, so any failure discards Customer, Vehicle, Service Order, Diagnosis and Estimate together.
 */
@Service
public class OpenServiceOrderIntakeUseCase {

    private final CustomerIntakePort customerIntakePort;
    private final VehicleIntakePort vehicleIntakePort;
    private final CatalogServiceQuotePort catalogServiceQuotePort;
    private final StockItemQuotePort stockItemQuotePort;
    private final CreateServiceOrderUseCase createServiceOrderUseCase;
    private final AssignDiagnosisAssigneeUseCase assignDiagnosisAssigneeUseCase;
    private final PerformDiagnosisUseCase performDiagnosisUseCase;
    private final GenerateEstimateUseCase generateEstimateUseCase;
    private final GetServiceOrderUseCase getServiceOrderUseCase;

    public OpenServiceOrderIntakeUseCase(
            CustomerIntakePort customerIntakePort,
            VehicleIntakePort vehicleIntakePort,
            CatalogServiceQuotePort catalogServiceQuotePort,
            StockItemQuotePort stockItemQuotePort,
            CreateServiceOrderUseCase createServiceOrderUseCase,
            AssignDiagnosisAssigneeUseCase assignDiagnosisAssigneeUseCase,
            PerformDiagnosisUseCase performDiagnosisUseCase,
            GenerateEstimateUseCase generateEstimateUseCase,
            GetServiceOrderUseCase getServiceOrderUseCase) {
        this.customerIntakePort = customerIntakePort;
        this.vehicleIntakePort = vehicleIntakePort;
        this.catalogServiceQuotePort = catalogServiceQuotePort;
        this.stockItemQuotePort = stockItemQuotePort;
        this.createServiceOrderUseCase = createServiceOrderUseCase;
        this.assignDiagnosisAssigneeUseCase = assignDiagnosisAssigneeUseCase;
        this.performDiagnosisUseCase = performDiagnosisUseCase;
        this.generateEstimateUseCase = generateEstimateUseCase;
        this.getServiceOrderUseCase = getServiceOrderUseCase;
    }

    @Transactional
    public ServiceOrderIntakeResponse execute(OpenServiceOrderIntakeRequest request) {
        List<IntakeServiceRequest> services = request.services() == null ? List.of() : request.services();
        if (!services.isEmpty() && request.diagnosisTechnicianId() == null) {
            throw new ServiceOrderIntakeInvalidDataException(
                    "O técnico responsável pelo diagnóstico é obrigatório quando há serviços");
        }

        CustomerIntakePort.CustomerIntake customer = identifyCustomer(request.customer());
        VehicleIntakePort.VehicleIntake vehicle = identifyVehicle(customer.customerId(), request.vehicle());
        UUID serviceOrderId = createServiceOrderUseCase.execute(new CreateServiceOrderRequest(
                customer.customerId(),
                vehicle.vehicleId(),
                new VehicleSnapshotRequest(vehicle.licensePlate(), vehicle.brand(), vehicle.model(), vehicle.year()),
                request.priority(),
                request.initialAssessment())).id();

        UUID estimateId = services.isEmpty()
                ? null
                : diagnoseAndEstimate(serviceOrderId, request.diagnosisTechnicianId(), services);

        return new ServiceOrderIntakeResponse(
                getServiceOrderUseCase.execute(serviceOrderId),
                new RegistrationReference(customer.customerId(),
                        customer.status() == CustomerIntakePort.Status.CREATED),
                new RegistrationReference(vehicle.vehicleId(), vehicle.status() == VehicleIntakePort.Status.CREATED),
                estimateId);
    }

    private CustomerIntakePort.CustomerIntake identifyCustomer(IntakeCustomerRequest request) {
        IntakeContactInfoRequest contact = request.contactInfo();
        CustomerIntakePort.CustomerIntake customer = customerIntakePort.identifyOrRegister(
                new CustomerIntakePort.CustomerData(
                        request.document(),
                        request.name(),
                        contact == null ? null : contact.email(),
                        contact == null ? null : contact.phone(),
                        contact == null ? null : toAddressData(contact.address())));
        if (customer.status() == CustomerIntakePort.Status.ARCHIVED) {
            throw new ServiceOrderIntakeCustomerArchivedException();
        }
        return customer;
    }

    private static CustomerIntakePort.AddressData toAddressData(IntakeAddressRequest address) {
        if (address == null) {
            return null;
        }
        return new CustomerIntakePort.AddressData(address.street(), address.number(), address.complement(),
                address.neighborhood(), address.city(), address.state(), address.postalCode());
    }

    private VehicleIntakePort.VehicleIntake identifyVehicle(UUID customerId, IntakeVehicleRequest request) {
        VehicleIntakePort.VehicleData data = new VehicleIntakePort.VehicleData(
                customerId, request.licensePlate(), request.chassis(), request.brand(), request.model(),
                request.year(), request.color(), request.mileage());
        VehicleIntakePort.VehicleIntake vehicle = vehicleIntakePort.identifyOrRegister(data);
        switch (vehicle.status()) {
            case REUSED, CREATED -> {
            }
            case ARCHIVED -> throw new ServiceOrderVehicleArchivedException();
            case OWNED_BY_ANOTHER_CUSTOMER -> throw new ServiceOrderIntakeVehicleOwnershipConflictException();
            case CHASSIS_ALREADY_REGISTERED -> throw new ServiceOrderIntakeVehicleChassisConflictException();
        }
        return vehicle;
    }

    private UUID diagnoseAndEstimate(UUID serviceOrderId, UUID technicianId, List<IntakeServiceRequest> services) {
        // Quote before changing the Service Order, so catalog and stock rejections happen before any diagnosis work.
        List<DiagnosisItemRequest> items = quotedDiagnosisItems(services);
        assignDiagnosisAssigneeUseCase.execute(serviceOrderId, new AssignDiagnosisAssigneeRequest(technicianId));
        ServiceOrderResponse diagnosed = performDiagnosisUseCase.execute(
                serviceOrderId, new PerformDiagnosisRequest(technicianId, items));
        // A new Service Order has exactly one diagnosis, so every execution belongs to it.
        UUID diagnosisId = diagnosed.executions().getFirst().diagnosisId();
        return generateEstimateUseCase.execute(serviceOrderId, diagnosisId).estimate().id();
    }

    private List<DiagnosisItemRequest> quotedDiagnosisItems(List<IntakeServiceRequest> services) {
        Map<UUID, CatalogServiceQuote> serviceQuotes = quoteCatalogServices(services);
        Map<UUID, StockItemQuote> partQuotes = quoteStockItems(services);
        return services.stream()
                .map(service -> {
                    CatalogServiceQuote quote = serviceQuotes.get(service.catalogServiceId());
                    return new DiagnosisItemRequest(service.catalogServiceId(), quote.name(), toMoneyDto(quote.price()),
                            parts(service).stream().map(part -> toStockRequirement(part, partQuotes)).toList());
                })
                .toList();
    }

    // Sorted IDs keep the lock order identical to PerformDiagnosisUseCase's eligibility check.
    private Map<UUID, CatalogServiceQuote> quoteCatalogServices(List<IntakeServiceRequest> services) {
        Map<UUID, CatalogServiceQuote> quotes = services.stream()
                .map(IntakeServiceRequest::catalogServiceId)
                .distinct()
                .sorted()
                .map(catalogServiceQuotePort::quote)
                .collect(Collectors.toMap(CatalogServiceQuote::id, Function.identity()));
        quotes.values().forEach(quote -> {
            switch (quote.eligibility()) {
                case ACTIVE -> {
                }
                case ARCHIVED -> throw new CatalogServiceArchivedForNewWorkException();
                case NOT_FOUND -> throw new CatalogServiceNotFoundForNewWorkException();
            }
        });
        return quotes;
    }

    private Map<UUID, StockItemQuote> quoteStockItems(List<IntakeServiceRequest> services) {
        List<UUID> stockItemIds = services.stream()
                .flatMap(service -> parts(service).stream())
                .map(IntakePartRequest::stockItemId)
                .distinct()
                .toList();
        if (stockItemIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, StockItemQuote> quotes = stockItemQuotePort.quote(stockItemIds);
        if (!quotes.keySet().containsAll(stockItemIds)) {
            throw new ServiceOrderStockItemNotFoundException();
        }
        if (quotes.values().stream().anyMatch(quote -> !quote.active())) {
            throw new ServiceOrderStockItemInactiveException();
        }
        return quotes;
    }

    private static List<IntakePartRequest> parts(IntakeServiceRequest service) {
        return service.parts() == null ? List.of() : service.parts();
    }

    private static StockRequirementRequest toStockRequirement(
            IntakePartRequest part, Map<UUID, StockItemQuote> quotes) {
        StockItemQuote quote = Objects.requireNonNull(quotes.get(part.stockItemId()));
        return new StockRequirementRequest(part.stockItemId(), quote.type(), part.quantity(), quote.name(),
                toMoneyDto(quote.price()));
    }

    private static MoneyDTO toMoneyDto(Money money) {
        return new MoneyDTO(money.value(), money.currency());
    }
}
