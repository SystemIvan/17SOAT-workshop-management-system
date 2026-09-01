package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.web;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.AssignDiagnosisAssigneeRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto
        .AverageServiceExecutionTimeResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.AssignTechnicianRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ChangeServiceOrderPriorityRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.CreateServiceOrderRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.FinalizeServiceOrderRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.PerformDiagnosisRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderStatusResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.StockRequirementRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.StockReservationAttemptMapper;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.StockReservationAttemptResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.UpdateExecutionProgressRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase.AssignDiagnosisAssigneeUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase.AssignTechnicianUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase.AttachStockRequirementUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase.ChangeServiceOrderPriorityUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase.CompleteExecutionUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase.CreateServiceOrderUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase.FinalizeServiceOrderUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase.GetServiceOrderStatusUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase.GetServiceOrderUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase
        .GetAverageServiceExecutionTimeUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase.ListServiceOrdersUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase.PerformDiagnosisUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase.RetryStockReservationUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase.StartExecutionUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase.UpdateExecutionProgressUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Priority;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrderStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderSearchCriteria;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/service-orders")
@Tag(name = "Service Orders", description = "Service lifecycle operations")
public class ServiceOrderController {

    private final CreateServiceOrderUseCase createServiceOrderUseCase;
    private final GetServiceOrderUseCase getServiceOrderUseCase;
    private final GetServiceOrderStatusUseCase getServiceOrderStatusUseCase;
    private final ListServiceOrdersUseCase listServiceOrdersUseCase;
    private final PerformDiagnosisUseCase performDiagnosisUseCase;
    private final ChangeServiceOrderPriorityUseCase changeServiceOrderPriorityUseCase;
    private final AssignTechnicianUseCase assignTechnicianUseCase;
    private final AssignDiagnosisAssigneeUseCase assignDiagnosisAssigneeUseCase;
    private final StartExecutionUseCase startExecutionUseCase;
    private final UpdateExecutionProgressUseCase updateExecutionProgressUseCase;
    private final CompleteExecutionUseCase completeExecutionUseCase;
    private final FinalizeServiceOrderUseCase finalizeServiceOrderUseCase;
    private final AttachStockRequirementUseCase attachStockRequirementUseCase;
    private final RetryStockReservationUseCase retryStockReservationUseCase;
    private final GetAverageServiceExecutionTimeUseCase getAverageServiceExecutionTimeUseCase;

    public ServiceOrderController(
            CreateServiceOrderUseCase createServiceOrderUseCase,
            GetServiceOrderUseCase getServiceOrderUseCase,
            GetServiceOrderStatusUseCase getServiceOrderStatusUseCase,
            ListServiceOrdersUseCase listServiceOrdersUseCase,
            PerformDiagnosisUseCase performDiagnosisUseCase,
            ChangeServiceOrderPriorityUseCase changeServiceOrderPriorityUseCase,
            AssignTechnicianUseCase assignTechnicianUseCase,
            AssignDiagnosisAssigneeUseCase assignDiagnosisAssigneeUseCase,
            StartExecutionUseCase startExecutionUseCase,
            UpdateExecutionProgressUseCase updateExecutionProgressUseCase,
            CompleteExecutionUseCase completeExecutionUseCase,
            FinalizeServiceOrderUseCase finalizeServiceOrderUseCase,
            AttachStockRequirementUseCase attachStockRequirementUseCase,
            RetryStockReservationUseCase retryStockReservationUseCase,
            GetAverageServiceExecutionTimeUseCase getAverageServiceExecutionTimeUseCase) {
        this.createServiceOrderUseCase = createServiceOrderUseCase;
        this.getServiceOrderUseCase = getServiceOrderUseCase;
        this.getServiceOrderStatusUseCase = getServiceOrderStatusUseCase;
        this.listServiceOrdersUseCase = listServiceOrdersUseCase;
        this.performDiagnosisUseCase = performDiagnosisUseCase;
        this.changeServiceOrderPriorityUseCase = changeServiceOrderPriorityUseCase;
        this.assignTechnicianUseCase = assignTechnicianUseCase;
        this.assignDiagnosisAssigneeUseCase = assignDiagnosisAssigneeUseCase;
        this.startExecutionUseCase = startExecutionUseCase;
        this.updateExecutionProgressUseCase = updateExecutionProgressUseCase;
        this.completeExecutionUseCase = completeExecutionUseCase;
        this.finalizeServiceOrderUseCase = finalizeServiceOrderUseCase;
        this.attachStockRequirementUseCase = attachStockRequirementUseCase;
        this.retryStockReservationUseCase = retryStockReservationUseCase;
        this.getAverageServiceExecutionTimeUseCase = getAverageServiceExecutionTimeUseCase;
    }

    @PostMapping
    @Operation(summary = "Criar uma ordem de serviço para um veículo ativo")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ordem de serviço criada"),
            @ApiResponse(responseCode = "400", description = "Dados da ordem de serviço inválidos"),
            @ApiResponse(responseCode = "404", description = "Veículo não encontrado"),
            @ApiResponse(responseCode = "409", description = "Veículo arquivado")
    })
    public ResponseEntity<ServiceOrderResponse> create(@Valid @RequestBody CreateServiceOrderRequest request) {
        ServiceOrderResponse response = createServiceOrderUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List service orders, optionally filtered by status, customer, technician or priority")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service orders listed"),
            @ApiResponse(responseCode = "400", description = "Invalid filter value")
    })
    public ResponseEntity<List<ServiceOrderResponse>> list(
            @RequestParam(required = false) ServiceOrderStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID technicianId,
            @RequestParam(required = false) Priority priority) {
        return ResponseEntity.ok(listServiceOrdersUseCase.execute(
                new ServiceOrderSearchCriteria(status, customerId, technicianId, priority)));
    }

    @GetMapping("/metrics/average-execution-time")
    @Operation(summary = "Get average service execution time in hours")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Execution-time averages returned"),
            @ApiResponse(responseCode = "400", description = "Invalid or missing period"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Manager or Admin role required")
    })
    public ResponseEntity<AverageServiceExecutionTimeResponse> getAverageExecutionTime(
            @Parameter(description = "Inclusive completion instant in ISO-8601 format", required = true)
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @Parameter(description = "Exclusive completion instant in ISO-8601 format", required = true)
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to) {
        return ResponseEntity.ok(getAverageServiceExecutionTimeUseCase.execute(from, to));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a service order by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service order found"),
            @ApiResponse(responseCode = "404", description = "Service order not found")
    })
    public ResponseEntity<ServiceOrderResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(getServiceOrderUseCase.execute(id));
    }

    @GetMapping("/{id}/status")
    @Operation(summary = "Get the current service order status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service order status found"),
            @ApiResponse(responseCode = "404", description = "Service order not found")
    })
    public ResponseEntity<ServiceOrderStatusResponse> getStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(getServiceOrderStatusUseCase.execute(id));
    }

    @PostMapping("/{id}/diagnosis")
    @Operation(summary = "Record a diagnosis and service executions")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Diagnosis recorded"),
            @ApiResponse(responseCode = "400", description = "Invalid diagnosis request"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Service order, technician, Catalog Service or Stock Item not found"),
            @ApiResponse(
                    responseCode = "409",
                    description = "Diagnosis is unavailable or a Catalog Service is archived")
    })
    public ResponseEntity<ServiceOrderResponse> performDiagnosis(
            @PathVariable UUID id, @Valid @RequestBody PerformDiagnosisRequest request) {
        return ResponseEntity.ok(performDiagnosisUseCase.execute(id, request));
    }

    @PutMapping("/{id}/diagnosis-assignee")
    @Operation(summary = "Assign the technician planned for the next diagnosis")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Diagnosis assignee assigned"),
            @ApiResponse(responseCode = "400", description = "Invalid or missing technicianId"),
            @ApiResponse(responseCode = "404", description = "Service order or technician not found"),
            @ApiResponse(responseCode = "409", description = "A diagnosis is already open")
    })
    public ResponseEntity<ServiceOrderResponse> assignDiagnosisAssignee(
            @PathVariable UUID id, @Valid @RequestBody AssignDiagnosisAssigneeRequest request) {
        return ResponseEntity.ok(assignDiagnosisAssigneeUseCase.execute(id, request));
    }

    @PatchMapping("/{id}/priority")
    @Operation(summary = "Change the priority of a service order")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Priority changed"),
            @ApiResponse(responseCode = "400", description = "Invalid or missing priority"),
            @ApiResponse(responseCode = "404", description = "Service order not found"),
            @ApiResponse(responseCode = "409", description = "Service order is completed or delivered")
    })
    public ResponseEntity<ServiceOrderResponse> changePriority(
            @PathVariable UUID id, @Valid @RequestBody ChangeServiceOrderPriorityRequest request) {
        return ResponseEntity.ok(changeServiceOrderPriorityUseCase.execute(id, request));
    }

    @PostMapping("/{id}/executions/{executionId}/assign-technician")
    @Operation(summary = "Assign a technician to a service execution")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Technician assigned"),
            @ApiResponse(responseCode = "400", description = "Invalid or missing technicianId"),
            @ApiResponse(responseCode = "404",
                    description = "Service order, service execution or technician not found"),
            @ApiResponse(responseCode = "409",
                    description = "Service execution is completed or rejected")
    })
    public ResponseEntity<ServiceOrderResponse> assignTechnician(
            @PathVariable UUID id, @PathVariable UUID executionId, @Valid @RequestBody AssignTechnicianRequest request) {
        return ResponseEntity.ok(assignTechnicianUseCase.execute(id, executionId, request));
    }

    @PostMapping("/{id}/executions/{executionId}/start")
    @Operation(summary = "Start a service execution")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service execution started"),
            @ApiResponse(responseCode = "404", description = "Service order or service execution not found"),
            @ApiResponse(responseCode = "409", description = "Service execution is not ready or has no technician assigned")
    })
    public ResponseEntity<ServiceOrderResponse> startExecution(
            @PathVariable UUID id, @PathVariable UUID executionId) {
        return ResponseEntity.ok(startExecutionUseCase.execute(id, executionId));
    }

    @PatchMapping("/{id}/executions/{executionId}/progress")
    @Operation(summary = "Update service execution progress")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service execution progress updated"),
            @ApiResponse(responseCode = "400", description = "Invalid or missing note"),
            @ApiResponse(responseCode = "404", description = "Service order or service execution not found"),
            @ApiResponse(responseCode = "409", description = "Service execution is not in the in-progress status")
    })
    public ResponseEntity<ServiceOrderResponse> updateExecutionProgress(
            @PathVariable UUID id, @PathVariable UUID executionId, @Valid @RequestBody UpdateExecutionProgressRequest request) {
        return ResponseEntity.ok(updateExecutionProgressUseCase.execute(id, executionId, request));
    }

    @PostMapping("/{id}/executions/{executionId}/complete")
    @Operation(summary = "Complete a service execution")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service execution completed"),
            @ApiResponse(responseCode = "404", description = "Service order or service execution not found"),
            @ApiResponse(responseCode = "409", description = "Service execution is not in the in-progress status")
    })
    public ResponseEntity<ServiceOrderResponse> completeExecution(
            @PathVariable UUID id, @PathVariable UUID executionId) {
        return ResponseEntity.ok(completeExecutionUseCase.execute(id, executionId));
    }

    @PostMapping("/{id}/executions/{executionId}/stock-requirements")
    @Operation(summary = "Attach a stock requirement to a service execution")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock requirement attached"),
            @ApiResponse(responseCode = "400", description = "Invalid or missing stock requirement fields"),
            @ApiResponse(responseCode = "404", description = "Service order or service execution not found"),
            @ApiResponse(responseCode = "409", description = "Service execution is completed or rejected")
    })
    public ResponseEntity<ServiceOrderResponse> attachStockRequirement(
            @PathVariable UUID id, @PathVariable UUID executionId, @Valid @RequestBody StockRequirementRequest request) {
        return ResponseEntity.ok(attachStockRequirementUseCase.execute(id, executionId, request));
    }

    @PostMapping("/{id}/executions/{executionId}/stock-reservation")
    @Operation(summary = "Retry the frozen stock reservation for a service execution")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservation attempt completed"),
            @ApiResponse(responseCode = "404", description = "Service order or service execution not found"),
            @ApiResponse(responseCode = "409", description = "Service execution state cannot retry a reservation")
    })
    public ResponseEntity<StockReservationAttemptResponse> retryStockReservation(
            @PathVariable UUID id, @PathVariable UUID executionId) {
        return ResponseEntity.ok(StockReservationAttemptMapper.toResponse(
                retryStockReservationUseCase.execute(id, executionId)));
    }

    @PostMapping("/{id}/finalize")
    @Operation(summary = "Finalize a service order")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service order finalized"),
            @ApiResponse(responseCode = "404", description = "Service order not found"),
            @ApiResponse(responseCode = "409", description = "Service order is not completed or the vehicle was not delivered")
    })
    public ResponseEntity<ServiceOrderResponse> finalize(
            @PathVariable UUID id, @Valid @RequestBody FinalizeServiceOrderRequest request) {
        return ResponseEntity.ok(finalizeServiceOrderUseCase.execute(id, request));
    }
}
