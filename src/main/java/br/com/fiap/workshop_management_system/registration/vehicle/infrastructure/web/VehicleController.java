package br.com.fiap.workshop_management_system.registration.vehicle.infrastructure.web;

import br.com.fiap.workshop_management_system.registration.vehicle.application.dto.CreateVehicleRequest;
import br.com.fiap.workshop_management_system.registration.vehicle.application.dto.UpdateVehicleRequest;
import br.com.fiap.workshop_management_system.registration.vehicle.application.dto.VehicleResponse;
import br.com.fiap.workshop_management_system.registration.vehicle.application.usecase.CreateVehicleUseCase;
import br.com.fiap.workshop_management_system.registration.vehicle.application.usecase.UpdateVehicleUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/vehicles")
@Tag(name = "Veículos", description = "Operações de cadastro de veículos")
public class VehicleController {

    private final CreateVehicleUseCase createVehicleUseCase;
    private final UpdateVehicleUseCase updateVehicleUseCase;

    public VehicleController(CreateVehicleUseCase createVehicleUseCase, UpdateVehicleUseCase updateVehicleUseCase) {
        this.createVehicleUseCase = createVehicleUseCase;
        this.updateVehicleUseCase = updateVehicleUseCase;
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
}
