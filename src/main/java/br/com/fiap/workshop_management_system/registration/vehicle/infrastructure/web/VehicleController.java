package br.com.fiap.workshop_management_system.registration.vehicle.infrastructure.web;

import br.com.fiap.workshop_management_system.registration.vehicle.application.dto.CreateVehicleRequest;
import br.com.fiap.workshop_management_system.registration.vehicle.application.dto.UpdateVehicleMileageRequest;
import br.com.fiap.workshop_management_system.registration.vehicle.application.dto.UpdateVehicleRequest;
import br.com.fiap.workshop_management_system.registration.vehicle.application.dto.VehicleResponse;
import br.com.fiap.workshop_management_system.registration.vehicle.application.usecase.ArchiveVehicleUseCase;
import br.com.fiap.workshop_management_system.registration.vehicle.application.usecase.CreateVehicleUseCase;
import br.com.fiap.workshop_management_system.registration.vehicle.application.usecase.GetVehicleUseCase;
import br.com.fiap.workshop_management_system.registration.vehicle.application.usecase.ListVehiclesUseCase;
import br.com.fiap.workshop_management_system.registration.vehicle.application.usecase.UpdateVehicleMileageUseCase;
import br.com.fiap.workshop_management_system.registration.vehicle.application.usecase.UpdateVehicleUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/vehicles")
@Tag(name = "Veículos", description = "Operações de cadastro, consulta e lifecycle de veículos")
public class VehicleController {

    private final CreateVehicleUseCase createVehicleUseCase;
    private final GetVehicleUseCase getVehicleUseCase;
    private final ListVehiclesUseCase listVehiclesUseCase;
    private final ArchiveVehicleUseCase archiveVehicleUseCase;
    private final UpdateVehicleUseCase updateVehicleUseCase;
    private final UpdateVehicleMileageUseCase updateVehicleMileageUseCase;

    public VehicleController(
            CreateVehicleUseCase createVehicleUseCase,
            GetVehicleUseCase getVehicleUseCase,
            ListVehiclesUseCase listVehiclesUseCase,
            ArchiveVehicleUseCase archiveVehicleUseCase,
            UpdateVehicleUseCase updateVehicleUseCase,
            UpdateVehicleMileageUseCase updateVehicleMileageUseCase) {
        this.createVehicleUseCase = createVehicleUseCase;
        this.getVehicleUseCase = getVehicleUseCase;
        this.listVehiclesUseCase = listVehiclesUseCase;
        this.archiveVehicleUseCase = archiveVehicleUseCase;
        this.updateVehicleUseCase = updateVehicleUseCase;
        this.updateVehicleMileageUseCase = updateVehicleMileageUseCase;
    }

    @PostMapping
    @Operation(summary = "Cadastrar veículo para um Customer ativo")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Veículo cadastrado"),
            @ApiResponse(responseCode = "400", description = "Dados do veículo inválidos"),
            @ApiResponse(responseCode = "404", description = "Customer não encontrado"),
            @ApiResponse(responseCode = "409", description = "Customer arquivado ou identidade duplicada")
    })
    public ResponseEntity<VehicleResponse> create(@Valid @RequestBody CreateVehicleRequest request) {
        VehicleResponse response = createVehicleUseCase.execute(request);
        return ResponseEntity.created(URI.create("/api/vehicles/" + response.id())).body(response);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Atualizar dados descritivos e chassis do veículo")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Veículo atualizado"),
            @ApiResponse(responseCode = "400", description = "Dados do veículo inválidos"),
            @ApiResponse(responseCode = "404", description = "Veículo não encontrado"),
            @ApiResponse(responseCode = "409", description = "Veículo arquivado ou chassis duplicado")
    })
    public ResponseEntity<VehicleResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateVehicleRequest request) {
        return ResponseEntity.ok(updateVehicleUseCase.execute(id, request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar veículo por ID, incluindo arquivados")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Veículo encontrado",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = VehicleResponse.class))),
            @ApiResponse(responseCode = "400", description = "ID inválido"),
            @ApiResponse(responseCode = "404", description = "Veículo não encontrado")
    })
    public ResponseEntity<VehicleResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(getVehicleUseCase.execute(id));
    }

    @GetMapping
    @Operation(summary = "Listar veículos ativos")
    @ApiResponse(
            responseCode = "200",
            description = "Veículos ativos listados",
            content = @Content(
                    mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = VehicleResponse.class))))
    public ResponseEntity<List<VehicleResponse>> list() {
        return ResponseEntity.ok(listVehiclesUseCase.execute());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Arquivar veículo de forma idempotente")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Veículo arquivado ou já arquivado"),
            @ApiResponse(responseCode = "400", description = "ID inválido"),
            @ApiResponse(responseCode = "404", description = "Veículo não encontrado")
    })
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        archiveVehicleUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/mileage")
    @Operation(summary = "Registrar ou atualizar a quilometragem do veículo")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Quilometragem registrada ou mantida"),
            @ApiResponse(responseCode = "400", description = "Quilometragem inválida"),
            @ApiResponse(responseCode = "404", description = "Veículo não encontrado"),
            @ApiResponse(responseCode = "409", description = "Veículo arquivado ou redução de quilometragem")
    })
    public ResponseEntity<VehicleResponse> updateMileage(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateVehicleMileageRequest request) {
        return ResponseEntity.ok(updateVehicleMileageUseCase.execute(id, request));
    }
}
