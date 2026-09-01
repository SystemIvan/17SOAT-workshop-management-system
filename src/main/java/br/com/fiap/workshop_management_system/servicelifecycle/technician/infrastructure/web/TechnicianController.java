package br.com.fiap.workshop_management_system.servicelifecycle.technician.infrastructure.web;

import br.com.fiap.workshop_management_system.servicelifecycle.technician.application.dto.CreateTechnicianRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.application.dto.RenameTechnicianRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.application.dto.TechnicianResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.application.dto.UpdateTechnicianStatusRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.application.usecase.CreateTechnicianUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.application.usecase.GetTechnicianUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.application.usecase.ListTechniciansUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.application.usecase.RenameTechnicianUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.application.usecase.UpdateTechnicianStatusUseCase;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/technicians")
@Tag(name = "Technicians", description = "Technician capabilities used by the service lifecycle")
public class TechnicianController {

    private final CreateTechnicianUseCase createTechnicianUseCase;
    private final GetTechnicianUseCase getTechnicianUseCase;
    private final ListTechniciansUseCase listTechniciansUseCase;
    private final RenameTechnicianUseCase renameTechnicianUseCase;
    private final UpdateTechnicianStatusUseCase updateTechnicianStatusUseCase;

    public TechnicianController(
            CreateTechnicianUseCase createTechnicianUseCase,
            GetTechnicianUseCase getTechnicianUseCase,
            ListTechniciansUseCase listTechniciansUseCase,
            RenameTechnicianUseCase renameTechnicianUseCase,
            UpdateTechnicianStatusUseCase updateTechnicianStatusUseCase) {
        this.createTechnicianUseCase = createTechnicianUseCase;
        this.getTechnicianUseCase = getTechnicianUseCase;
        this.listTechniciansUseCase = listTechniciansUseCase;
        this.renameTechnicianUseCase = renameTechnicianUseCase;
        this.updateTechnicianStatusUseCase = updateTechnicianStatusUseCase;
    }

    @PostMapping
    @Operation(summary = "Create a technician")
    public ResponseEntity<TechnicianResponse> create(@Valid @RequestBody CreateTechnicianRequest request) {
        TechnicianResponse response = createTechnicianUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a technician by ID")
    public ResponseEntity<TechnicianResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(getTechnicianUseCase.execute(id));
    }

    @GetMapping
    @Operation(summary = "List technicians")
    public ResponseEntity<List<TechnicianResponse>> list() {
        return ResponseEntity.ok(listTechniciansUseCase.execute());
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Rename a technician")
    public ResponseEntity<TechnicianResponse> rename(@PathVariable UUID id, @Valid @RequestBody RenameTechnicianRequest request) {
        return ResponseEntity.ok(renameTechnicianUseCase.execute(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update technician status")
    public ResponseEntity<TechnicianResponse> updateStatus(
            @PathVariable UUID id, @Valid @RequestBody UpdateTechnicianStatusRequest request) {
        return ResponseEntity.ok(updateTechnicianStatusUseCase.execute(id, request));
    }
}
