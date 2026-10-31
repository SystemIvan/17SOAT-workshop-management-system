package br.com.fiap.workshop_management_system.servicelifecycle.intake.infrastructure.web;

import br.com.fiap.workshop_management_system.servicelifecycle.intake.application.dto.OpenServiceOrderIntakeRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.intake.application.dto.ServiceOrderIntakeResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.intake.application.usecase
        .OpenServiceOrderIntakeUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Lives in its own controller, outside ServiceOrderController, so serviceorder does not depend on intake and the
 * intake → serviceorder/estimate dependency stays acyclic. It shares the /api/service-orders base path.
 */
@RestController
@RequestMapping("/api/service-orders")
@Tag(name = "Service Orders", description = "Service lifecycle operations")
public class ServiceOrderIntakeController {

    private final OpenServiceOrderIntakeUseCase openServiceOrderIntakeUseCase;

    public ServiceOrderIntakeController(OpenServiceOrderIntakeUseCase openServiceOrderIntakeUseCase) {
        this.openServiceOrderIntakeUseCase = openServiceOrderIntakeUseCase;
    }

    @PostMapping("/intake")
    @Operation(
            summary = "Abrir ordem de serviço completa (cliente, veículo, serviços e peças)",
            description = "Identifica o cliente por CPF/CNPJ e o veículo pela placa, cadastrando-os se não "
                    + "existirem, e abre a ordem de serviço. Com serviços, registra o diagnóstico com nome e preço "
                    + "vindos do catálogo e do estoque e gera o orçamento automaticamente. Tudo ocorre numa única "
                    + "transação: qualquer rejeição desfaz a operação inteira.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ordem de serviço aberta"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou técnico ausente com serviços"),
            @ApiResponse(responseCode = "401", description = "Autenticação obrigatória"),
            @ApiResponse(responseCode = "403", description = "Papel MANAGER ou ADMIN obrigatório"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Técnico, serviço do catálogo ou item de estoque não encontrado"),
            @ApiResponse(
                    responseCode = "409",
                    description = "Cliente ou veículo arquivado, placa de outro cliente, chassi já cadastrado, "
                            + "serviço arquivado, item de estoque inativo ou cadastro concorrente")
    })
    public ResponseEntity<ServiceOrderIntakeResponse> open(@Valid @RequestBody OpenServiceOrderIntakeRequest request) {
        ServiceOrderIntakeResponse response = openServiceOrderIntakeUseCase.execute(request);
        return ResponseEntity.created(URI.create("/api/service-orders/" + response.serviceOrder().id()))
                .body(response);
    }
}
