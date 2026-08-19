package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.web;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.AssignTechnicianRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.CreateServiceOrderRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.FinalizeServiceOrderRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.PerformDiagnosisRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderStatusResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.UpdateExecutionProgressRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase.AssignTechnicianUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase.CompleteExecutionUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase.CreateServiceOrderUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase.FinalizeServiceOrderUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase.GetServiceOrderStatusUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase.GetServiceOrderUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase.PerformDiagnosisUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase.StartExecutionUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase.UpdateExecutionProgressUseCase;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/service-orders")
@Tag(name = "Service Orders", description = "Service lifecycle operations")
public class ServiceOrderController {

    private final CreateServiceOrderUseCase createServiceOrderUseCase;
    private final GetServiceOrderUseCase getServiceOrderUseCase;
    private final GetServiceOrderStatusUseCase getServiceOrderStatusUseCase;
    private final PerformDiagnosisUseCase performDiagnosisUseCase;
    private final AssignTechnicianUseCase assignTechnicianUseCase;
    private final StartExecutionUseCase startExecutionUseCase;
    private final UpdateExecutionProgressUseCase updateExecutionProgressUseCase;
    private final CompleteExecutionUseCase completeExecutionUseCase;
    private final FinalizeServiceOrderUseCase finalizeServiceOrderUseCase;

    public ServiceOrderController(
            CreateServiceOrderUseCase createServiceOrderUseCase,
            GetServiceOrderUseCase getServiceOrderUseCase,
            GetServiceOrderStatusUseCase getServiceOrderStatusUseCase,
            PerformDiagnosisUseCase performDiagnosisUseCase,
            AssignTechnicianUseCase assignTechnicianUseCase,
            StartExecutionUseCase startExecutionUseCase,
            UpdateExecutionProgressUseCase updateExecutionProgressUseCase,
            CompleteExecutionUseCase completeExecutionUseCase,
            FinalizeServiceOrderUseCase finalizeServiceOrderUseCase) {
        this.createServiceOrderUseCase = createServiceOrderUseCase;
        this.getServiceOrderUseCase = getServiceOrderUseCase;
        this.getServiceOrderStatusUseCase = getServiceOrderStatusUseCase;
        this.performDiagnosisUseCase = performDiagnosisUseCase;
        this.assignTechnicianUseCase = assignTechnicianUseCase;
        this.startExecutionUseCase = startExecutionUseCase;
        this.updateExecutionProgressUseCase = updateExecutionProgressUseCase;
        this.completeExecutionUseCase = completeExecutionUseCase;
        this.finalizeServiceOrderUseCase = finalizeServiceOrderUseCase;
    }

    @PostMapping
    @Operation(summary = "Create a service order")
    public ResponseEntity<ServiceOrderResponse> create(@Valid @RequestBody CreateServiceOrderRequest request) {
        ServiceOrderResponse response = createServiceOrderUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
    public ResponseEntity<ServiceOrderResponse> performDiagnosis(
            @PathVariable UUID id, @Valid @RequestBody PerformDiagnosisRequest request) {
        return ResponseEntity.ok(performDiagnosisUseCase.execute(id, request));
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
            @ApiResponse(responseCode = "409", description = "Service execution is not in the ready status")
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
