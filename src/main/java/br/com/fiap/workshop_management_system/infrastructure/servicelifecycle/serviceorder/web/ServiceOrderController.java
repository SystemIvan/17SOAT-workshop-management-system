package br.com.fiap.workshop_management_system.infrastructure.servicelifecycle.serviceorder.web;

import br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.dto.AssignTechnicianRequest;
import br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.dto.CreateServiceOrderRequest;
import br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.dto.FinalizeServiceOrderRequest;
import br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.dto.PerformDiagnosisRequest;
import br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.dto.ServiceOrderStatusResponse;
import br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.dto.UpdateExecutionProgressRequest;
import br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.usecase.AssignTechnicianUseCase;
import br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.usecase.CompleteExecutionUseCase;
import br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.usecase.CreateServiceOrderUseCase;
import br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.usecase.FinalizeServiceOrderUseCase;
import br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.usecase.GetServiceOrderStatusUseCase;
import br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.usecase.GetServiceOrderUseCase;
import br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.usecase.PerformDiagnosisUseCase;
import br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.usecase.StartExecutionUseCase;
import br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.usecase.UpdateExecutionProgressUseCase;
import jakarta.validation.Valid;
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
    public ResponseEntity<ServiceOrderResponse> create(@Valid @RequestBody CreateServiceOrderRequest request) {
        ServiceOrderResponse response = createServiceOrderUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceOrderResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(getServiceOrderUseCase.execute(id));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<ServiceOrderStatusResponse> getStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(getServiceOrderStatusUseCase.execute(id));
    }

    @PostMapping("/{id}/diagnosis")
    public ResponseEntity<ServiceOrderResponse> performDiagnosis(
            @PathVariable UUID id, @Valid @RequestBody PerformDiagnosisRequest request) {
        return ResponseEntity.ok(performDiagnosisUseCase.execute(id, request));
    }

    @PostMapping("/{id}/executions/{executionId}/assign-technician")
    public ResponseEntity<ServiceOrderResponse> assignTechnician(
            @PathVariable UUID id, @PathVariable UUID executionId, @Valid @RequestBody AssignTechnicianRequest request) {
        return ResponseEntity.ok(assignTechnicianUseCase.execute(id, executionId, request));
    }

    @PostMapping("/{id}/executions/{executionId}/start")
    public ResponseEntity<ServiceOrderResponse> startExecution(
            @PathVariable UUID id, @PathVariable UUID executionId) {
        return ResponseEntity.ok(startExecutionUseCase.execute(id, executionId));
    }

    @PatchMapping("/{id}/executions/{executionId}/progress")
    public ResponseEntity<ServiceOrderResponse> updateExecutionProgress(
            @PathVariable UUID id, @PathVariable UUID executionId, @Valid @RequestBody UpdateExecutionProgressRequest request) {
        return ResponseEntity.ok(updateExecutionProgressUseCase.execute(id, executionId, request));
    }

    @PostMapping("/{id}/executions/{executionId}/complete")
    public ResponseEntity<ServiceOrderResponse> completeExecution(
            @PathVariable UUID id, @PathVariable UUID executionId) {
        return ResponseEntity.ok(completeExecutionUseCase.execute(id, executionId));
    }

    @PostMapping("/{id}/finalize")
    public ResponseEntity<ServiceOrderResponse> finalize(
            @PathVariable UUID id, @Valid @RequestBody FinalizeServiceOrderRequest request) {
        return ResponseEntity.ok(finalizeServiceOrderUseCase.execute(id, request));
    }
}
