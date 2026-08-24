package br.com.fiap.workshop_management_system.registration.servicecatalog.infrastructure.web;

import br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto.CatalogServiceResponse;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto.CreateCatalogServiceRequest;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto.RenameCatalogServiceRequest;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto
        .UpdateCatalogServiceBasePriceRequest;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.usecase
        .ArchiveCatalogServiceUseCase;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.usecase
        .CreateCatalogServiceUseCase;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.usecase.GetCatalogServiceUseCase;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.usecase
        .ListCatalogServicesUseCase;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.usecase
        .RenameCatalogServiceUseCase;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.usecase
        .UpdateCatalogServiceBasePriceUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
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
@RequestMapping("/api/catalog-services")
@Tag(name = "Catálogo de Serviços", description = "Cadastro e consulta dos serviços oferecidos pela oficina")
public class CatalogServiceController {

    private final CreateCatalogServiceUseCase createCatalogServiceUseCase;
    private final ArchiveCatalogServiceUseCase archiveCatalogServiceUseCase;
    private final GetCatalogServiceUseCase getCatalogServiceUseCase;
    private final ListCatalogServicesUseCase listCatalogServicesUseCase;
    private final RenameCatalogServiceUseCase renameCatalogServiceUseCase;
    private final UpdateCatalogServiceBasePriceUseCase updateCatalogServiceBasePriceUseCase;

    public CatalogServiceController(
            CreateCatalogServiceUseCase createCatalogServiceUseCase,
            ArchiveCatalogServiceUseCase archiveCatalogServiceUseCase,
            GetCatalogServiceUseCase getCatalogServiceUseCase,
            ListCatalogServicesUseCase listCatalogServicesUseCase,
            RenameCatalogServiceUseCase renameCatalogServiceUseCase,
            UpdateCatalogServiceBasePriceUseCase updateCatalogServiceBasePriceUseCase) {
        this.createCatalogServiceUseCase = createCatalogServiceUseCase;
        this.archiveCatalogServiceUseCase = archiveCatalogServiceUseCase;
        this.getCatalogServiceUseCase = getCatalogServiceUseCase;
        this.listCatalogServicesUseCase = listCatalogServicesUseCase;
        this.renameCatalogServiceUseCase = renameCatalogServiceUseCase;
        this.updateCatalogServiceBasePriceUseCase = updateCatalogServiceBasePriceUseCase;
    }

    @PostMapping
    @Operation(summary = "Cadastrar serviço no catálogo")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Serviço cadastrado",
                    headers = @Header(name = "Location", description = "URI canônica do serviço criado"),
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CatalogServiceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dados do serviço inválidos"),
            @ApiResponse(responseCode = "409", description = "Nome já cadastrado")
    })
    public ResponseEntity<CatalogServiceResponse> create(
            @Valid @RequestBody CreateCatalogServiceRequest request) {
        CatalogServiceResponse response = createCatalogServiceUseCase.execute(request);
        return ResponseEntity.created(URI.create("/api/catalog-services/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar serviço do catálogo por ID")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Serviço encontrado",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CatalogServiceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Identificador inválido"),
            @ApiResponse(responseCode = "404", description = "Serviço não encontrado")
    })
    public ResponseEntity<CatalogServiceResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(getCatalogServiceUseCase.execute(id));
    }

    @GetMapping
    @Operation(summary = "Listar serviços ativos do catálogo")
    @ApiResponse(
            responseCode = "200",
            description = "Serviços ativos listados",
            content = @Content(
                    mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = CatalogServiceResponse.class))))
    public ResponseEntity<List<CatalogServiceResponse>> list() {
        return ResponseEntity.ok(listCatalogServicesUseCase.execute());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Arquivar serviço do catálogo")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Serviço arquivado ou já estava arquivado"),
            @ApiResponse(responseCode = "400", description = "Identificador inválido"),
            @ApiResponse(responseCode = "404", description = "Serviço não encontrado")
    })
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        archiveCatalogServiceUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Renomear serviço do catálogo")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Serviço renomeado",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CatalogServiceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Nome ou identificador inválido"),
            @ApiResponse(responseCode = "404", description = "Serviço não encontrado"),
            @ApiResponse(responseCode = "409", description = "Nome já cadastrado ou serviço arquivado")
    })
    public ResponseEntity<CatalogServiceResponse> rename(
            @PathVariable UUID id,
            @Valid @RequestBody RenameCatalogServiceRequest request) {
        return ResponseEntity.ok(renameCatalogServiceUseCase.execute(id, request));
    }

    @PatchMapping("/{id}/base-price")
    @Operation(summary = "Atualizar preço-base do serviço")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Preço-base atualizado",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CatalogServiceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Preço ou identificador inválido"),
            @ApiResponse(responseCode = "404", description = "Serviço não encontrado"),
            @ApiResponse(responseCode = "409", description = "Serviço arquivado")
    })
    public ResponseEntity<CatalogServiceResponse> updateBasePrice(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCatalogServiceBasePriceRequest request) {
        return ResponseEntity.ok(updateCatalogServiceBasePriceUseCase.execute(id, request));
    }
}
