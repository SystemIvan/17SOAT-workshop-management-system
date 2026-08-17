package br.com.fiap.workshop_management_system.registration.customer.infrastructure.web;

import br.com.fiap.workshop_management_system.registration.customer.application.dto.CreateCustomerRequest;
import br.com.fiap.workshop_management_system.registration.customer.application.dto.CustomerResponse;
import br.com.fiap.workshop_management_system.registration.customer.application.dto.RenameCustomerRequest;
import br.com.fiap.workshop_management_system.registration.customer.application.dto.UpdateCustomerContactRequest;
import br.com.fiap.workshop_management_system.registration.customer.application.usecase.CreateCustomerUseCase;
import br.com.fiap.workshop_management_system.registration.customer.application.usecase.GetCustomerUseCase;
import br.com.fiap.workshop_management_system.registration.customer.application.usecase.IdentifyCustomerByTaxIdUseCase;
import br.com.fiap.workshop_management_system.registration.customer.application.usecase.ListCustomersUseCase;
import br.com.fiap.workshop_management_system.registration.customer.application.usecase.RenameCustomerUseCase;
import br.com.fiap.workshop_management_system.registration.customer.application.usecase.UpdateCustomerContactUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
@Tag(name = "Clientes", description = "Operações de cadastro de clientes")
public class CustomerController {

    private final CreateCustomerUseCase createCustomerUseCase;
    private final GetCustomerUseCase getCustomerUseCase;
    private final IdentifyCustomerByTaxIdUseCase identifyCustomerByTaxIdUseCase;
    private final ListCustomersUseCase listCustomersUseCase;
    private final RenameCustomerUseCase renameCustomerUseCase;
    private final UpdateCustomerContactUseCase updateCustomerContactUseCase;

    public CustomerController(
            CreateCustomerUseCase createCustomerUseCase,
            GetCustomerUseCase getCustomerUseCase,
            IdentifyCustomerByTaxIdUseCase identifyCustomerByTaxIdUseCase,
            ListCustomersUseCase listCustomersUseCase,
            RenameCustomerUseCase renameCustomerUseCase,
            UpdateCustomerContactUseCase updateCustomerContactUseCase) {
        this.createCustomerUseCase = createCustomerUseCase;
        this.getCustomerUseCase = getCustomerUseCase;
        this.identifyCustomerByTaxIdUseCase = identifyCustomerByTaxIdUseCase;
        this.listCustomersUseCase = listCustomersUseCase;
        this.renameCustomerUseCase = renameCustomerUseCase;
        this.updateCustomerContactUseCase = updateCustomerContactUseCase;
    }

    @PostMapping
    @Operation(summary = "Cadastrar cliente")
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerResponse response = createCustomerUseCase.execute(request);
        return ResponseEntity.created(URI.create("/api/customers/" + response.id())).body(response);
    }

    @GetMapping("/identify")
    @Operation(summary = "Identificar cliente por CPF ou CNPJ")
    public ResponseEntity<CustomerResponse> identify(
            @RequestParam @Parameter(description = "CPF ou CNPJ, formatado ou apenas números") String document) {
        return ResponseEntity.ok(identifyCustomerByTaxIdUseCase.execute(document));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar cliente por ID")
    public ResponseEntity<CustomerResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(getCustomerUseCase.execute(id));
    }

    @GetMapping
    @Operation(summary = "Listar clientes")
    public ResponseEntity<List<CustomerResponse>> list() {
        return ResponseEntity.ok(listCustomersUseCase.execute());
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Alterar nome do cliente")
    public ResponseEntity<CustomerResponse> rename(
            @PathVariable UUID id, @Valid @RequestBody RenameCustomerRequest request) {
        return ResponseEntity.ok(renameCustomerUseCase.execute(id, request));
    }

    @PatchMapping("/{id}/contact-info")
    @Operation(
            summary = "Atualizar informações de contato do cliente",
            description = "Atualiza parcialmente e-mail, telefone ou endereço; campos omitidos são preservados")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Informações de contato atualizadas"),
            @ApiResponse(responseCode = "400", description = "Dados de contato inválidos"),
            @ApiResponse(responseCode = "404", description = "Cliente não encontrado")
    })
    public ResponseEntity<CustomerResponse> updateContactInfo(
            @PathVariable UUID id, @Valid @RequestBody UpdateCustomerContactRequest request) {
        return ResponseEntity.ok(updateCustomerContactUseCase.execute(id, request));
    }
}
